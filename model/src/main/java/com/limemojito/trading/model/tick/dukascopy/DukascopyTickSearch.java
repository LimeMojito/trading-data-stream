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
import com.limemojito.trading.model.tick.Tick;
import com.limemojito.trading.model.tick.TickVisitor;
import com.limemojito.trading.model.tick.dukascopy.criteria.Criteria;
import com.limemojito.trading.model.tick.dukascopy.criteria.TickCriteria;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Locates and streams Dukascopy tick data over a requested time window.
 * <p>
 * Uses a {@link DukascopyPathGenerator} to enumerate hourly data files and a {@link DukascopyCache}
 * to provide the underlying bytes, returning a combined {@link com.limemojito.trading.model.TradingInputStream}
 * of {@link com.limemojito.trading.model.tick.Tick} objects filtered to the requested time range.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class DukascopyTickSearch extends BaseDukascopySearch {
    private final Validator validator;
    private final DukascopyCache cache;
    private final DukascopyPathGenerator pathGenerator;

    /**
     * Stream ticks for a symbol within the given inclusive time range.
     *
     * @param symbol      instrument symbol (e.g. EURUSD)
     * @param startTime   inclusive start instant (UTC)
     * @param endTime     inclusive end instant (UTC)
     * @param tickVisitor optional callback invoked for each produced tick
     * @return a combined stream of ticks from all matching Dukascopy files
     */
    public TradingInputStream<Tick> search(String symbol, Instant startTime, Instant endTime, TickVisitor tickVisitor) {
        final TickCriteria criteria = buildTickCriteria(symbol, startTime, endTime);
        log.debug("Forming tick stream for {} {} -> {}", criteria.getSymbol(), criteria.getStart(), criteria.getEnd());
        final List<String> paths = pathGenerator.generatePaths(symbol, startTime, endTime);
        final TradingInputStream<Tick> ticks = search(criteria.getSymbol(),
                                                      paths,
                                                      tick -> filterAgainst(criteria, tick),
                                                      tickVisitor);
        log.info("Returning tick stream for {} {} -> {}", criteria.getSymbol(), criteria.getStart(), criteria.getEnd());
        return ticks;
    }

    /**
     * Compose a combined tick stream from a precomputed list of Dukascopy file paths.
     *
     * @param symbol            instrument symbol for logging/trace purposes
     * @param paths             ordered list of Dukascopy hourly file paths
     * @param tickSearchFilter  filter applied to each decoded tick before emission
     * @param tickVisitor       optional visitor invoked for each emitted tick
     * @return a combined {@link TradingInputStream} over all provided paths
     */
    public TradingInputStream<Tick> search(String symbol,
                                           List<String> paths,
                                           Predicate<Tick> tickSearchFilter,
                                           TickVisitor tickVisitor) {
        final Iterator<String> pathIterator = paths.iterator();
        final Iterator<TradingInputStream<Tick>> tickStreamIterator = new Iterator<>() {
            @Override
            public boolean hasNext() {
                return pathIterator.hasNext();
            }

            @Override
            public TradingInputStream<Tick> next() {
                return new DukascopyTickInputStream(validator, cache, pathIterator.next(), tickVisitor);
            }
        };
        log.info("Returning tick stream for {} {} -> {}",
                 symbol,
                 paths.getFirst(),
                 paths.getLast());
        return TradingInputStream.combine(tickStreamIterator, tickSearchFilter);
    }

    private TickCriteria buildTickCriteria(String symbol, Instant startTime, Instant endTime) {
        return new TickCriteria(symbol, startTime, endTime);
    }

    private static boolean filterAgainst(Criteria criteria, Tick tick) {
        Instant tickInstant = tick.getInstant();
        Instant criteriaStart = criteria.getStart();
        Instant criteriaEnd = criteria.getEnd();
        return (tickInstant.equals(criteriaStart) || tickInstant.isAfter(criteriaStart))
               && (tickInstant.isBefore(criteriaEnd) || tickInstant.equals(criteriaEnd));
    }
}
