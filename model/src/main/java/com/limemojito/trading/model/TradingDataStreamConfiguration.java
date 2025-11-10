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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.bar.TickBarNotifyingAggregator;
import com.limemojito.trading.model.tick.dukascopy.DukascopyCache;
import com.limemojito.trading.model.tick.dukascopy.DukascopyPathGenerator;
import com.limemojito.trading.model.tick.dukascopy.DukascopySearch;
import com.limemojito.trading.model.tick.dukascopy.DukascopyUtils;
import com.limemojito.trading.model.tick.dukascopy.cache.DirectDukascopyNoCache;
import com.limemojito.trading.model.tick.dukascopy.cache.LocalDukascopyCache;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * An example Spring configuration with a local file cache.  You can replace the local cache chain with an S3-&gt;local-&gt;no-cache as well.
 *
 * @see com.limemojito.trading.model.tick.dukascopy.cache.S3DukascopyCache
 */
@Configuration
public class TradingDataStreamConfiguration {
    /**
     * Generator for Dukascopy file paths used by search and caching components.
     *
     * @return a new {@link DukascopyPathGenerator}
     */
    @Bean
    public DukascopyPathGenerator pathGenerator() {
        return new DukascopyPathGenerator();
    }

    /**
     * Local cache chain that first checks the filesystem and falls back to direct (no-cache) retrieval.
     *
     * @param mapper Jackson object mapper for metadata persistence
     * @return a {@link DukascopyCache} that reads/writes to local disk and falls back to network on misses
     */
    @Bean
    public DukascopyCache localCacheChain(ObjectMapper mapper) {
        return new LocalDukascopyCache(mapper, new DirectDukascopyNoCache());
    }

    /**
     * Search implementation backed by Dukascopy data and the configured cache chain.
     *
     * @param pathGenerator generator for data paths
     * @param cache         cache implementation to read/write data
     * @param validator     bean validation instance used by aggregators
     * @return a {@link TradingSearch} implementation
     */
    @Bean
    public TradingSearch tickSearch(DukascopyPathGenerator pathGenerator, DukascopyCache cache, Validator validator) {
        return new DukascopySearch(validator, cache, pathGenerator);
    }

    /**
     * Aggregates ticks into bars and notifies a callback as each bar completes.
     * Prototype scope so each request gets a fresh aggregator instance.
     *
     * @param validator         bean validation instance
     * @param notifier          callback notified when bars complete
     * @param aggregationPeriod target bar period for aggregation
     * @return a {@link TickBarNotifyingAggregator}
     */
    @Scope("prototype")
    @Bean
    public TickBarNotifyingAggregator tickBarAggregator(Validator validator,
                                                        TickBarNotifyingAggregator.BarNotifier notifier,
                                                        @Value("${tick-to-bar.aggregation.period}") Bar.Period aggregationPeriod) {
        return new TickBarNotifyingAggregator(validator, notifier, aggregationPeriod);
    }

    /**
     * A helper method for a quick standalone configuration (not in a spring container).  This should NOT be used in a spring container as it
     * generates a Validator, object mapper, etc and is primarily suited for testing.
     *
     * @return A DukascopySearch backed TradingSearch instance using local file caching.
     * @see DukascopyUtils#standaloneSetup()
     * @see DukascopySearch
     */
    public static TradingSearch standaloneSearch() {
        return DukascopyUtils.standaloneSetup();
    }
}
