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

import com.limemojito.trading.model.CacheStatistics;
import com.limemojito.trading.model.CacheStatistics.AggregateCacheStatistics;
import com.limemojito.trading.model.CacheStatistics.SimpleCacheStatistics;
import com.limemojito.trading.model.tick.dukascopy.DukascopyCache;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static com.limemojito.trading.model.CacheStatistics.STAT_HIT;
import static com.limemojito.trading.model.CacheStatistics.STAT_MISS;

/**
 * Base decorator that adds a simple read-through caching layer in front of another {@link DukascopyCache}.
 * <p>
 * Subclasses implement storage-specific {@code checkCache} and {@code saveToCache}. When a path is requested,
 * this class first checks the local cache; on miss it delegates to the fallback, stores the result, and returns it.
 * Hit/miss statistics are tracked for observability via {@link #cacheStats()}.
 * </p>
 */
@Slf4j
public abstract class FallbackDukascopyCache implements DukascopyCache {

    private final SimpleCacheStatistics directStats;
    @Getter(AccessLevel.PROTECTED)
    private final DukascopyCache fallback;
    @Getter
    private final AggregateCacheStatistics cacheStatistics;

    /**
     * Create a new cache decorator that will fall back to the supplied {@link DukascopyCache}
     * when a requested path is not available in the local cache.
     *
     * @param fallback the downstream cache/source to query on cache miss; must not be {@code null}
     */
    public FallbackDukascopyCache(DukascopyCache fallback) {
        this.fallback = fallback;
        this.directStats = new SimpleCacheStatistics(getClass().getSimpleName());
        this.cacheStatistics = CacheStatistics.combine(directStats, fallback.getCacheStatistics());
    }

    @Override
    public InputStream stream(String dukascopyPath) throws IOException {
        final InputStream rv;
        final Optional<InputStream> stream = checkCache(dukascopyPath);
        if (stream.isEmpty()) {
            log.debug("Cache miss for {}", dukascopyPath);
            directStats.incrementStat(STAT_MISS);
            rv = new ByteArrayInputStream(saveDataFromFallback(dukascopyPath));
        } else {
            log.debug("Cache hit for {}", dukascopyPath);
            directStats.incrementStat(STAT_HIT);
            rv = stream.get();
        }
        return rv;
    }

    /**
     * Persist the provided data stream in the concrete cache implementation so that subsequent
     * calls to {@link #checkCache(String)} for the same path can be served locally.
     * <p>
     * Implementations should not close the provided {@code input}; lifecycle is managed by the caller.
     * </p>
     *
     * @param dukascopyPath path key under which the data should be cached
     * @param input         the data to cache (positioned at start)
     * @throws IOException if persisting to the cache fails
     */
    protected abstract void saveToCache(String dukascopyPath, InputStream input) throws IOException;

    /**
     * Check the cache and return an open input stream if present.
     *
     * @param dukascopyPath path to check in cache
     * @return Optional.empty() if not present
     * @throws IOException on an io failure.
     */
    protected abstract Optional<InputStream> checkCache(String dukascopyPath) throws IOException;

    private byte[] saveDataFromFallback(String dukascopyPath) throws IOException {
        try (InputStream fallbackStream = fallback.stream(dukascopyPath)) {
            final byte[] data = IOUtils.toByteArray(fallbackStream);
            try (InputStream input = new ByteArrayInputStream(data)) {
                saveToCache(dukascopyPath, input);
            }
            return data;
        }
    }
}
