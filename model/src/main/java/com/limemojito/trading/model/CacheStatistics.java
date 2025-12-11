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

package com.limemojito.trading.model;

import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;

/**
 * Cache statistics interface for checking cache performance.
 */
public interface CacheStatistics {
    /**
     * Name of the hit stats
     */
    String STAT_HIT = "hit";

    /**
     * Name of the miss stats
     */
    String STAT_MISS = "miss";

    /**
     * Double representation of one hundred.
     */
    double ONE_HUNDRED = 100.0;

    /**
     * Name of the cache.
     *
     * @return a name for display purposes
     *
     */
    String getName();

    /**
     * Number of hits made in cache usage.
     *
     * @return number of hits.
     *
     */
    int getHitCount();

    /**
     * Number of misses made in cache usage.
     *
     * @return number of hits.
     *
     */
    int getMissCount();

    /**
     * Number of retrieves made in cache usage.
     *
     * @return number of hits.
     *
     */
    int getRetrieveCount();


    /**
     * Retrieve the value of a named stat.
     *
     * @return number against the stat.
     *
     */
    int getStat(String statName);

    /**
     * Displayable cache statistics.
     *
     * @return Statistics as a string.
     *
     */
    String cacheStats();

    /**
     * The hit rate of the cache expresses as a percentage.
     *
     * @return the hit rate of the cache as a percentage.
     */
    double getHitRate();

    /**
     * Retrieve sub cache information.
     *
     * @return empty map if no sub caches.
     */
    Map<String, CacheStatistics> getIndividualCacheStatistics();

    /**
     * Combine to caches statistics into one sum object.
     *
     * @param a First cache
     * @param b Second cache
     * @return A combined statistics counter that delegates to the two caches.
     */
    static AggregateCacheStatistics combine(CacheStatistics a, CacheStatistics b) {
        return new AggregateCacheStatistics(a, b);
    }

    /**
     * A basic cache statistics implementation.
     */
    class SimpleCacheStatistics implements CacheStatistics {
        private static final int STATS_CAPACITY = 128;
        @Getter
        private final String name;
        private final ConcurrentMap<String, AtomicInteger> statMap;

        /**
         * Create a new cache statistics object with "hit" and "miss" stats.
         *
         * @param name name of the cache
         */
        public SimpleCacheStatistics(String name) {
            this(name, new String[0]);
        }

        /**
         * Create a new cache statistics object with "hit", "miss", and a selection of additional stats.
         *
         * @param name name of the cache
         */
        public SimpleCacheStatistics(String name, String... statNames) {
            this.name = name;
            this.statMap = new ConcurrentHashMap<>();
            this.statMap.put(STAT_HIT, new AtomicInteger());
            this.statMap.put(STAT_MISS, new AtomicInteger());
            for (String statName : statNames) {
                statMap.put(statName, new AtomicInteger());
            }
        }

        /**
         * Increment a named stat by one.
         *
         * @param statName name of stat to increment.
         */
        public void incrementStat(String statName) {
            statMap.computeIfAbsent(statName, k -> new AtomicInteger()).incrementAndGet();
        }

        @Override
        public int getStat(String statName) {
            return statMap.computeIfAbsent(statName, k -> new AtomicInteger()).get();
        }

        @Override
        public int getHitCount() {
            return getStat(STAT_HIT);
        }

        @Override
        public int getMissCount() {
            return getStat(STAT_MISS);
        }

        @Override
        public int getRetrieveCount() {
            return getHitCount() + getMissCount();
        }

        @Override
        public double getHitRate() {
            return (getHitCount() / (double) getRetrieveCount()) * ONE_HUNDRED;
        }

        @Override
        public Map<String, CacheStatistics> getIndividualCacheStatistics() {
            return Map.of();
        }

        @Override
        public String cacheStats() {
            StringBuilder sb = new StringBuilder(STATS_CAPACITY);
            sb.append(name).append(": ");
            sb.append("retrieve: ").append(getRetrieveCount());
            statMap.keySet()
                   .stream()
                   .sorted()
                   .forEach(key -> sb.append(format(", %s: %d", key, statMap.get(key).get())));
            return sb.toString();
        }
    }

    class AggregateCacheStatistics implements CacheStatistics {
        private final CacheStatistics a;
        private final CacheStatistics b;

        public AggregateCacheStatistics(CacheStatistics a, CacheStatistics b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public int getHitCount() {
            return a.getHitCount() + b.getHitCount();
        }

        @Override
        public int getMissCount() {
            return a.getMissCount() + b.getMissCount();
        }

        @Override
        public int getRetrieveCount() {
            return a.getRetrieveCount() + b.getRetrieveCount();
        }

        @Override
        public double getHitRate() {
            return (getHitCount() / (double) getRetrieveCount()) * ONE_HUNDRED;
        }

        @Override
        public int getStat(String statName) {
            return a.getStat(statName) + b.getStat(statName);
        }

        @Override
        public String getName() {
            return "%s-%s".formatted(a.getName(), b.getName());
        }

        /**
         * Get each sub cache statistics.
         *
         * @return Map of cache statistics by cache name.
         */
        public Map<String, CacheStatistics> getIndividualCacheStatistics() {
            return Map.of(a.getName(), a, b.getName(), b);
        }

        @Override
        public String cacheStats() {
            return "%s, %s".formatted(a.cacheStats(), b.cacheStats());
        }
    }
}
