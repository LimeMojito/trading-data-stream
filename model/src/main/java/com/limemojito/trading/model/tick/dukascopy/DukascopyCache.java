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

package com.limemojito.trading.model.tick.dukascopy;

import com.limemojito.trading.model.CacheStatistics;
import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.tick.dukascopy.criteria.BarCriteria;
import jakarta.validation.Validator;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Retrieve data from the Dukascopy bank, taking advantage of a cache configuration.
 */
public interface DukascopyCache {

    /**
     * Statistics for the cache.
     *
     * @return cache statistics.
     */
    CacheStatistics getCacheStatistics();

    /**
     * Tick data for the supplied dukascopy path.
     *
     * @param dukascopyPath path relative to the Dukascopy data root
     * @return the stream found.
     * @throws IOException on a data fetch failure.
     * @see DukascopyPathGenerator
     */
    InputStream stream(String dukascopyPath) throws IOException;

    /**
     * Creates a bar cache of the same configuration as this cache.
     *
     * @param validator  Bean validator.
     * @param tickSearch Search interface.
     * @return Bar Cache implementation.
     *
     */
    BarCache createBarCache(Validator validator, DukascopyTickSearch tickSearch);

    /**
     * Interface for retrieving bars from a cache.
     */
    interface BarCache {

        /**
         * Statistics for the cache.
         *
         * @return cache statistics.
         */
        CacheStatistics getCacheStatistics();

        /**
         * Retrieves bars from cache or direct tick data conversion.
         *
         * @param criteria   bar query to fulfill
         * @param dayOfPaths Day of paths to retrieve.
         * @return A list of bars in descending time order.
         * @throws IOException on an IO failure
         * @see DukascopyPathGenerator
         */
        List<Bar> getOneDayOfTicksAsBar(BarCriteria criteria, List<String> dayOfPaths) throws IOException;
    }

}
