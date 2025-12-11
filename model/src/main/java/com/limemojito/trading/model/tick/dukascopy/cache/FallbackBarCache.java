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
import com.limemojito.trading.model.CacheStatistics.SimpleCacheStatistics;
import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.tick.dukascopy.DukascopyCache;
import com.limemojito.trading.model.tick.dukascopy.criteria.BarCriteria;
import lombok.Getter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.limemojito.trading.model.CacheStatistics.STAT_HIT;
import static com.limemojito.trading.model.CacheStatistics.STAT_MISS;

/**
 * A caching strategy that falls back to another strategy on a cache miss.
 */
public abstract class FallbackBarCache implements DukascopyCache.BarCache {
    private final DukascopyCache.BarCache fallback;
    @Getter
    private final CacheStatistics cacheStatistics;
    private final SimpleCacheStatistics directStats;

    /**
     * Create a new fallback bar cache configuration using the supplied fallback strategy.
     *
     * @param fallback Strategy to fall back to if cache miss.
     */
    public FallbackBarCache(DukascopyCache.BarCache fallback) {
        this.fallback = fallback;
        this.directStats = new SimpleCacheStatistics(getClass().getSimpleName());
        this.cacheStatistics = CacheStatistics.combine(directStats,
                                                       fallback.getCacheStatistics());
    }

    @Override
    public List<Bar> getOneDayOfTicksAsBar(BarCriteria criteria, List<String> dayOfPaths) throws IOException {
        if (dayOfPaths.isEmpty()) {
            return Collections.emptyList();
        }
        final List<Bar> rv;
        Optional<List<Bar>> bars = checkCache(criteria, dayOfPaths.getFirst());
        if (bars.isEmpty()) {
            directStats.incrementStat(STAT_MISS);
            rv = saveDataFromFallback(criteria, dayOfPaths);
        } else {
            directStats.incrementStat(STAT_HIT);
            rv = bars.get();
        }
        return rv;
    }

    /**
     * Save supplied data to the cache.
     *
     * @param criteria      Criteria of the bar search made.
     * @param dukascopyPath path of the dukascopy bar result.
     * @param oneDayOfBars  one day worth of bar results.
     * @throws IOException on an IO failure.
     */
    protected abstract void saveToCache(BarCriteria criteria,
                                        String dukascopyPath,
                                        List<Bar> oneDayOfBars) throws IOException;

    /**
     * Check the cache and return an open input stream if present.
     *
     * @param criteria              path to check in cache
     * @param firstDukascopyDayPath Path of the first 1H ticks to check against cache.
     * @return NULL if not present - we can have empty file sets.
     * @throws IOException on an io failure.
     */
    protected abstract Optional<List<Bar>> checkCache(BarCriteria criteria,
                                                      String firstDukascopyDayPath) throws IOException;

    private List<Bar> saveDataFromFallback(BarCriteria criteria, List<String> dukascopyPaths) throws IOException {
        List<Bar> data = fallback.getOneDayOfTicksAsBar(criteria, dukascopyPaths);
        saveToCache(criteria, dukascopyPaths.getFirst(), data);
        return data;
    }
}
