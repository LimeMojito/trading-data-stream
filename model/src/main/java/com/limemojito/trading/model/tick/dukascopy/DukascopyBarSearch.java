/*
 * Copyright 2011-2024 Lime Mojito Pty Ltd
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
import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.bar.BarListInputStream;
import com.limemojito.trading.model.bar.BarVisitor;
import com.limemojito.trading.model.tick.dukascopy.DukascopyCache.BarCache;
import com.limemojito.trading.model.tick.dukascopy.criteria.BarCriteria;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import static com.limemojito.trading.model.bar.Bar.Period.D1;

/**
 * Searches Dukascopy tick data and returns aggregated bars over a date range.
 * <p>
 * This class orchestrates path generation, cache retrieval, and trimming to the requested time window,
 * returning a combined {@link com.limemojito.trading.model.TradingInputStream} of {@link Bar} objects.
 * </p>
 */
@RequiredArgsConstructor
@Slf4j
public class DukascopyBarSearch extends BaseDukascopySearch {
    private final BarCache cache;
    private final DukascopyPathGenerator pathGenerator;

    /**
     * Aggregate Dukascopy ticks into {@link Bar} data over the given time range.
     * <p>
     * The underlying cache is consulted per-day; results are trimmed to the exact
     * {@code [startTime, endTime]} window and combined into a single streaming iterator.
     * </p>
     *
     * @param symbol      instrument symbol (e.g. EURUSD)
     * @param period      bar aggregation period
     * @param startTime   inclusive start instant (UTC)
     * @param endTime     inclusive end instant (UTC)
     * @param barVisitor  optional callback invoked for each produced bar
     * @return a stream of bars across the requested range
     * @throws IOException if the underlying data cannot be read
     */
    public TradingInputStream<Bar> searchForDaysIn(String symbol,
                                                   Bar.Period period,
                                                   Instant startTime,
                                                   Instant endTime,
                                                   BarVisitor barVisitor) throws IOException {
        BarCriteria criteria = buildBarCriteria(symbol, period, startTime, endTime);
        log.debug("Forming bar stream for {} {} {} -> {}",
                  criteria.getSymbol(),
                  criteria.getPeriod(),
                  criteria.getStart(),
                  criteria.getEnd());
        final Predicate<Bar> trimFilter = bar ->
                bar.getStartInstant().compareTo(criteria.getStart()) >= 0
                && bar.getStartInstant().compareTo(criteria.getEnd()) <= 0;
        final BarVisitor barVisitAfterTrim = bar -> {
            if (trimFilter.test(bar)) {
                barVisitor.visit(bar);
            }
        };
        log.debug("Retrieving day of paths from {} to {}", criteria.getDayStart(), criteria.getDayEnd());
        final List<TradingInputStream<Bar>> barInputStreams = new LinkedList<>();
        for (int i = 0; i < criteria.getNumDays(); i++) {
            addOneDayOfBars(symbol, criteria, barVisitAfterTrim, barInputStreams, i);
        }
        final TradingInputStream<Bar> barStream = TradingInputStream.combine(barInputStreams.iterator(), trimFilter);
        log.info("Returning bar stream for {} {} {} -> {}",
                 criteria.getSymbol(),
                 criteria.getPeriod(),
                 criteria.getStart(),
                 criteria.getEnd());
        return barStream;
    }

    private void addOneDayOfBars(String symbol,
                                 BarCriteria criteria,
                                 BarVisitor barVisitAfterTrim,
                                 List<TradingInputStream<Bar>> barInputStreams,
                                 int datIndex) throws IOException {
        final List<String> dayPaths = pathGenerator.generatePaths(symbol,
                                                                  criteria.getDayStart(datIndex),
                                                                  criteria.getDayEnd(datIndex));
        final List<Bar> oneDayOfBarStream = cache.getOneDayOfTicksAsBar(criteria, dayPaths);
        if (oneDayOfBarStream.size() > criteria.getPeriod().periodsIn(D1)) {
            throw new IllegalStateException("Unexpected number of bars " + oneDayOfBarStream.size());
        }
        if (!oneDayOfBarStream.isEmpty()) {
            barInputStreams.add(new BarListInputStream(oneDayOfBarStream, barVisitAfterTrim));
        }
    }

    private BarCriteria buildBarCriteria(String symbol, Bar.Period period, Instant startTime, Instant endTime) {
        return new BarCriteria(symbol, period, startTime, endTime);
    }
}
