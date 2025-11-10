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

import com.limemojito.trading.model.TradingInputStream;
import com.limemojito.trading.model.TradingSearch;
import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.bar.BarInputStreamToCsv;
import com.limemojito.trading.model.bar.BarVisitor;
import com.limemojito.trading.model.tick.Tick;
import com.limemojito.trading.model.tick.TickVisitor;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;

/**
 * High-level search service that retrieves Dukascopy ticks and aggregates them into bars.
 * <p>
 * This service wires together path generation, caching, and the tick/bar search pipelines to provide
 * a simple API: stream raw {@link Tick} data or aggregate into {@link Bar}s over a requested time range.
 * </p>
 */
@Service
@Slf4j
public class DukascopySearch extends BaseDukascopySearch implements TradingSearch {
    public static final String DEFAULT_BEGINNING_OF_TIME = BaseDukascopySearch.DEFAULT_BEGINNING_OF_TIME;
    private final DukascopyTickSearch tickSearch;
    private final DukascopyBarSearch barSearch;
    private final DukascopyCache cache;
    private final DukascopyCache.BarCache barCache;

    /**
     * Creates a new Dukascopy based search engine.
     *
     * @param validator     Validates generated objects.
     * @param cache         Caching strategy for model objects.
     * @param pathGenerator Dukascopy path generator to use for data retrieval.
     */
    public DukascopySearch(Validator validator,
                           DukascopyCache cache,
                           DukascopyPathGenerator pathGenerator) {
        this.tickSearch = new DukascopyTickSearch(validator, cache, pathGenerator);
        this.cache = cache;
        this.barCache = cache.createBarCache(validator, tickSearch);
        this.barSearch = new DukascopyBarSearch(barCache, pathGenerator);
    }

    public String cacheStats() {
        return "Tick Cache: " + cache.cacheStats() + "\nBar  Cache: " + barCache.cacheStats();
    }

    @Override
    public void setTheBeginningOfTime(Instant theBeginningOfTime) {
        super.setTheBeginningOfTime(theBeginningOfTime);
        tickSearch.setTheBeginningOfTime(theBeginningOfTime);
        barSearch.setTheBeginningOfTime(theBeginningOfTime);
    }

    /**
     * A simple search using local cache, generating bars
     *
     * @param args symbol, period, start (yyyy-MM-ddTHH:mm:ssZ), end (yyyy-MM-ddTHH:mm:ssZ)
     * @throws IOException on an IO failure.
     */
    @SuppressWarnings("MagicNumber")
    public static void main(String... args) throws IOException {
        final String symbol = args[0];
        final Bar.Period period = Bar.Period.valueOf(args[1]);
        final Instant startTime = Instant.parse(args[2]);
        final Instant endTime = Instant.parse(args[3]);
        DukascopySearch search = DukascopyUtils.standaloneSetup();
        final int initialSize = 32 * 1024;
        final StringWriter stringWriter = new StringWriter(initialSize);
        try (TradingInputStream<Bar> ticks = search.aggregateFromTicks(symbol, period, startTime, endTime);
             BarInputStreamToCsv barCsv = new BarInputStreamToCsv(ticks, stringWriter)) {
            barCsv.convert();
        }
        System.out.println();
        System.out.println(stringWriter);
    }

    @Override
    public TradingInputStream<Tick> search(String symbol, Instant startTime, Instant endTime, TickVisitor tickVisitor) {
        assertCriteriaTimes(startTime, endTime);
        return tickSearch.search(symbol, startTime, endTime, tickVisitor);
    }

    @Override
    public TradingInputStream<Bar> aggregateFromTicks(String symbol,
                                                      Bar.Period period,
                                                      Instant startTime,
                                                      Instant endTime,
                                                      BarVisitor barVisitor) throws IOException {
        assertCriteriaTimes(startTime, endTime);
        return barSearch.searchForDaysIn(symbol, period, startTime, endTime, barVisitor);
    }
}
