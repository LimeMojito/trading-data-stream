/*
 * Copyright 2011-2025 Lime Mojito Pty Ltd
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package com.limemojito.trading.model.tick.dukascopy.cache;

import com.google.common.util.concurrent.RateLimiter;
import com.limemojito.trading.model.CacheStatistics;
import com.limemojito.trading.model.CacheStatistics.SimpleCacheStatistics;
import com.limemojito.trading.model.MarketStatus;
import com.limemojito.trading.model.tick.dukascopy.DukascopyCache;
import com.limemojito.trading.model.tick.dukascopy.DukascopyPathGenerator;
import com.limemojito.trading.model.tick.dukascopy.DukascopyTickSearch;
import jakarta.annotation.Nonnull;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.URI;
import java.time.Instant;

import static com.limemojito.trading.model.CacheStatistics.STAT_MISS;
import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.System.getProperty;

/**
 * This is no caching and a direct call to dukascopy.  Rate limited to work with dukascopy servers.
 */
@Slf4j
@SuppressWarnings("UnstableApiUsage")
public class DirectDukascopyNoCache implements DukascopyCache {
    /**
     * Defaults to 0.9 ps which plays nicely with Dukascopy. Otherwise, they simply stop responding (50X) if you hit the
     * servers too hard, or they do a sneaky 30s delay before data is returned.  This has a LONG timeout if it occurs.
     */
    public static final String PROP_PERMITS = DirectDukascopyNoCache.class.getPackageName() + ".permits";

    /**
     * Defaults to 3 attempts (exponential backoff).
     */
    public static final String PROP_RETRY_COUNT = DirectDukascopyNoCache.class.getPackageName() + ".retryCount";

    /**
     * Defaults to <a href="https://datafeed.dukascopy.com/datafeed/">...</a> which plays nicely with Dukascopy.
     * Otherwise, they delay data requests by at least 30s before they start
     * responding if you hit the servers too hard.  Note the slash on the end is required.
     */
    public static final String PROP_URL = DirectDukascopyNoCache.class.getPackageName() + ".url";

    /**
     * Statistic key for retry count.
     */
    public static final String STAT_RETRY = "retry";

    /**
     * 2025/11/10 Note there are many ways to get rate limited. Smells like the limit is requests per min or larger period.
     */
    private static final double PERMITS_PER_SECOND = parseDouble(getProperty(PROP_PERMITS, "0.9"));
    private static final int RETRY_COUNT = parseInt(getProperty(PROP_RETRY_COUNT, "3"));
    private static final String DUKASCOPY_URL = getProperty(PROP_URL, "https://datafeed.dukascopy.com/datafeed/");
    private static final int IO_BUFFER_SIZE = 32 * 1024;
    private static final RateLimiter RATE_LIMITER = RateLimiter.create(PERMITS_PER_SECOND);
    public static final int VERY_LONG_PAUSE = 60;
    public static final int LONG_PAUSE = 30;
    private final SimpleCacheStatistics cacheStatistics;
    private final MarketStatus marketStatus;
    private final DukascopyPathGenerator pathGenerator;

    /**
     * Creates a new direct Dukascopy fetch configuration with rate limiting.
     */
    public DirectDukascopyNoCache() {
        this.marketStatus = new MarketStatus();
        this.pathGenerator = new DukascopyPathGenerator(marketStatus);
        this.cacheStatistics = new SimpleCacheStatistics(getClass().getSimpleName(), STAT_RETRY);
        log.info("DirectDukascopyNoCache permits/s: {} retryCount: {} url: {}",
                 PERMITS_PER_SECOND,
                 RETRY_COUNT,
                 DUKASCOPY_URL);
    }

    @Override
    public CacheStatistics getCacheStatistics() {
        return cacheStatistics;
    }

    /**
     * Open a buffered stream to the Dukascopy resource identified by the path, honoring the rate limiter
     * and retry policy for transient server errors.  Note that the market must be open for the supplied path to be
     * fetched from Dukascopy due to rate limiting.
     *
     * @param dukascopyPath path relative to the Dukascopy data root
     * @return buffered input stream to the remote resource
     * @throws IOException if the resource cannot be retrieved
     * @see MarketStatus
     */
    @Override
    public InputStream stream(String dukascopyPath) throws IOException {
        if (marketIsOpen(dukascopyPath)) {
            final DataSource url = new UrlDataSource(DUKASCOPY_URL + dukascopyPath);
            // play nice with Dukascopy's free data.  And if you don't, they stop sending data.
            BufferedInputStream stream = fetchWithRetry(url, 1);
            cacheStatistics.incrementStat(STAT_MISS);
            return stream;
        } else {
            log.debug("Empty stream is market is closed.");
            return emptyStream();
        }
    }

    /**
     * Create a bar cache that does no caching and loads directly from Dukascopy.
     *
     * @param validator  bean validator for bar data
     * @param tickSearch provider capable of locating Dukascopy bar files
     * @return a bar cache implementation without caching
     */
    @Override
    public BarCache createBarCache(Validator validator, DukascopyTickSearch tickSearch) {
        return new DirectDukascopyBarNoCache(validator, tickSearch);
    }

    /**
     * Exposed for testing
     */
    interface DataSource {
        /**
         * Get data from data source.
         *
         * @return Data from data source.
         * @throws IOException on an io failure.
         */
        InputStream openStream() throws IOException;
    }

    /**
     * Exposed for testing.  Fetch URL from dukascopy with retry.
     *
     * @param url       URL to fetch data from
     * @param callCount number of calls attempted so far
     * @return The data stream on success
     * @throws IOException On an IO failure.
     */
    BufferedInputStream fetchWithRetry(DataSource url, int callCount) throws IOException {
        try {
            // keep the rate limit here as extra insurance during retries
            log.debug("Rate limit: {}/s attempt acquire", RATE_LIMITER.getRate());
            final double waited = RATE_LIMITER.acquire();
            log.debug("Rate limit: waited {}s", waited);
            log.info("Loading from {}", url);
            return new BufferedInputStream(url.openStream(), IO_BUFFER_SIZE);
        } catch (IOException e) {
            final int pauseSeconds = checkExceptionForRateLimitInfo(e, callCount);
            if (pauseSeconds > 0 && callCount <= RETRY_COUNT) {
                waitForRetry(e, pauseSeconds);
                cacheStatistics.incrementStat(STAT_RETRY);
                return fetchWithRetry(url, ++callCount);
            } else {
                throw e;
            }
        }
    }

    private boolean marketIsOpen(String dukascopyPath) {
        Instant timeUtc = pathGenerator.pathToInstant(dukascopyPath);
        return marketStatus.isOpen(timeUtc) == MarketStatus.Status.OPEN;
    }

    private static @Nonnull ByteArrayInputStream emptyStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    /**
     * Supply a backoff base time according to the response from server.
     * * 503 - 60 seconds
     * * 504 - 120 seconds
     * Other errors - configured pause seconds
     *
     * @param e exception
     * @return seconds to backoff or -1 for unknown state.
     */
    private static int checkExceptionForRateLimitInfo(IOException e, int callCount) {
        if (e instanceof SocketException) {
            log.warn("Server connection socket failure: {}", e.getMessage());
            return VERY_LONG_PAUSE * callCount;
        }
        if (e.getMessage() != null) {
            final String errorMessage = e.getMessage();
            log.warn("Server connection error message: {}", errorMessage);
            if (errorMessage.contains("500")
                    || errorMessage.contains("501")
                    || errorMessage.contains("502")
                    || errorMessage.contains("503")) {
                return LONG_PAUSE * callCount;
            } else if (errorMessage.contains("504")) {
                return VERY_LONG_PAUSE * callCount;
            }
        }
        log.warn("Unexpected failure", e);
        return VERY_LONG_PAUSE * callCount;
    }

    private static void waitForRetry(IOException e, double pauseSeconds) throws IOException {
        try {
            log.info("pausing for {}s to retry", pauseSeconds);
            final double toMilliseconds = 1000.0;
            Thread.sleep((long) (pauseSeconds * toMilliseconds));
        } catch (InterruptedException ex) {
            log.warn("Interrupted wait of {}s", pauseSeconds);
            throw e;
        }
    }

    private record UrlDataSource(String url) implements DataSource {
        @Override
        public InputStream openStream() throws IOException {
            return URI.create(url).toURL().openStream();
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public String toString() {
            return url;
        }
    }
}
