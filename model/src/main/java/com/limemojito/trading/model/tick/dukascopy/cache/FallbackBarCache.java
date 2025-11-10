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

import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.tick.dukascopy.DukascopyCache;
import com.limemojito.trading.model.tick.dukascopy.criteria.BarCriteria;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class FallbackBarCache implements DukascopyCache.BarCache {
    private final DukascopyCache.BarCache fallback;
    private final AtomicInteger cacheMiss;
    private final AtomicInteger cacheHit;
    private final AtomicInteger retrieveCount;

    public FallbackBarCache(DukascopyCache.BarCache fallback) {
        this.fallback = fallback;
        this.cacheMiss = new AtomicInteger();
        this.cacheHit = new AtomicInteger();
        this.retrieveCount = new AtomicInteger();
    }

    @Override
    public List<Bar> getOneDayOfTicksAsBar(BarCriteria criteria, List<String> dayOfPaths) throws IOException {
        List<Bar> bars = checkCache(criteria, dayOfPaths.getFirst());
        if (bars == null) {
            cacheMiss.incrementAndGet();
            bars = saveDataFromFallback(criteria, dayOfPaths);
        } else {
            cacheHit.incrementAndGet();
        }
        retrieveCount.incrementAndGet();
        return bars;
    }

    @Override
    public int getHitCount() {
        return cacheHit.get();
    }

    @Override
    public int getMissCount() {
        return cacheMiss.get();
    }

    @Override
    public int getRetrieveCount() {
        return retrieveCount.get();
    }

    @Override
    public String cacheStats() {
        final double toPercent = 100.0;
        return String.format("%s %d %dh %dm %.2f%% -> (%s)",
                             getClass().getSimpleName(),
                             getRetrieveCount(),
                             getHitCount(),
                             getMissCount(),
                             getRetrieveCount() > 0 ? (getHitCount() / (double) getRetrieveCount()) * toPercent : 0,
                             fallback.cacheStats());
    }

    protected abstract void saveToCache(BarCriteria criteria,
                                        String dukascopyPath,
                                        List<Bar> oneDayOfBars) throws IOException;

    /**
     * @param criteria              path to check in cache
     * @param firstDukascopyDayPath Path of the first 1H ticks to check against cache.
     * @return NULL if not present - we can have empty file sets.
     * @throws IOException on an io failure.
     */
    protected abstract List<Bar> checkCache(BarCriteria criteria, String firstDukascopyDayPath) throws IOException;

    private List<Bar> saveDataFromFallback(BarCriteria criteria, List<String> dukascopyPaths) throws IOException {
        List<Bar> data = fallback.getOneDayOfTicksAsBar(criteria, dukascopyPaths);
        saveToCache(criteria, dukascopyPaths.getFirst(), data);
        return data;
    }
}
