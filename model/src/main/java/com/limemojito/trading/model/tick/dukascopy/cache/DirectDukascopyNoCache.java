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
import com.limemojito.trading.model.tick.dukascopy.DukascopyCache;
import com.limemojito.trading.model.tick.dukascopy.DukascopyTickSearch;
import jakarta.validation.Validator;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.util.concurrent.RateLimiter.create;
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
     * Defaults to 1.0 ps which plays nicely with Dukascopy. Otherwise, they simply stop responding (50X) if you hit the
     * servers too hard, or they do a sneaky 30s delay before data is returned.  This has a LONG timeout if it occurs.
     */
    public static final String PROP_PERMITS = DirectDukascopyNoCache.class.getPackageName() + ".permits";

    /**
     * Defaults to 30.0 s to pause if server 500 is encountered.  This may indicate that we are over rate.
     * Exponential back-off over the number of retries.
     *
     * @see #PROP_RETRY_COUNT
     */
    public static final String PROP_RETRY = DirectDukascopyNoCache.class.getPackageName() + ".retrySeconds";

    /**
     * Defaults to 3 attempts (exponential backoff).
     *
     * @see #PROP_RETRY
     */
    public static final String PROP_RETRY_COUNT = DirectDukascopyNoCache.class.getPackageName() + ".retryCount";

    /**
     * Defaults to <a href="https://datafeed.dukascopy.com/datafeed/">...</a> which plays nicely with Dukascopy.
     * Otherwise, they delay data requests by at least 30s before they start
     * responding if you hit the servers too hard.  Note the slash on the end is required.
     */
    public static final String PROP_URL = DirectDukascopyNoCache.class.getPackageName() + ".url";

    /**
     * 2025/11/10 Note higher than 1.0 produces back-offs and then 30s delays
     */
    private static final double PERMITS_PER_SECOND = parseDouble(getProperty(PROP_PERMITS, "1.0"));

    private static final double PAUSE_SECONDS = parseDouble(getProperty(PROP_RETRY, "30.0"));
    private static final int RETRY_COUNT = parseInt(getProperty(PROP_RETRY_COUNT, "3"));
    private static final RateLimiter RATE_LIMITER = create(PERMITS_PER_SECOND);
    private static final String DUKASCOPY_URL = getProperty(PROP_URL, "https://datafeed.dukascopy.com/datafeed/");
    private static final int IO_BUFFER_SIZE = 32 * 1024;

    private final AtomicInteger retryCounter;
    private final AtomicInteger retrievePathCounter;

    public DirectDukascopyNoCache() {
        retryCounter = new AtomicInteger();
        retrievePathCounter = new AtomicInteger();
        log.info("DirectDukascopyNoCache permits/s: {} retrySeconds: {} retryCount: {} url: {}",
                 PERMITS_PER_SECOND,
                 PAUSE_SECONDS,
                 RETRY_COUNT,
                 DUKASCOPY_URL);
    }

    /**
     * Open a buffered stream to the Dukascopy resource identified by the path, honoring the rate limiter
     * and retry policy for transient server errors.
     *
     * @param dukascopyPath path relative to the Dukascopy data root
     * @return buffered input stream to the remote resource
     * @throws IOException if the resource cannot be retrieved
     */
    @Override
    public InputStream stream(String dukascopyPath) throws IOException {
        final DataSource url = new UrlDataSource(DUKASCOPY_URL + dukascopyPath);
        // play nice with Dukascopy's free data.  And if you don't they stop sending data.
        BufferedInputStream stream = fetchWithRetry(url, 1);
        retrievePathCounter.incrementAndGet();
        return stream;
    }

    /**
     * Number of successful remote resource retrievals performed in this process.
     */
    @Override
    public int getRetrieveCount() {
        return retrievePathCounter.get();
    }

    /**
     * Cache hits are always zero for the no-cache implementation.
     */
    @Override
    public int getHitCount() {
        return 0;
    }

    /**
     * Cache misses equal the number of retrievals for the no-cache implementation.
     */
    @Override
    public int getMissCount() {
        return getRetrieveCount();
    }

    /**
     * Number of retry attempts made due to server errors.
     */
    public int getRetryCount() {
        return retryCounter.get();
    }

    /**
     * Human-readable summary of cache activity including retrievals and retries.
     */
    @Override
    public String cacheStats() {
        return String.format("DirectDukascopyNoCache: %d retrieve(s) %d retry(s)", getRetrieveCount(), getRetryCount());
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
        InputStream openStream() throws IOException;
    }

    @Value
    @SuppressWarnings("RedundantModifiersValueLombok")
    private static final class UrlDataSource implements DataSource {
        private final String url;

        @Override
        public InputStream openStream() throws IOException {
            return URI.create(url).toURL().openStream();
        }

        public String toString() {
            return url;
        }
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
            if (e.getMessage() != null && e.getMessage().contains("500") && callCount <= RETRY_COUNT) {
                waitForRetry(e, callCount);
                retryCounter.getAndIncrement();
                return fetchWithRetry(url, ++callCount);
            } else {
                throw e;
            }
        }
    }

    private static void waitForRetry(IOException e, int callCount) throws IOException {
        final double pauseSeconds = PAUSE_SECONDS * callCount;
        try {
            log.info("Dukascopy server error: {}", e.getMessage());
            log.warn("pausing for {}s to retry", pauseSeconds);
            final double toMilliseconds = 1000.0;
            Thread.sleep((long) (pauseSeconds * toMilliseconds));
        } catch (InterruptedException ex) {
            log.info("Interrupted wait of {}s", pauseSeconds);
            throw e;
        }
    }
}
