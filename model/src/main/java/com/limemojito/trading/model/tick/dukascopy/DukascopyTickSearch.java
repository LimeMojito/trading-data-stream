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

@Slf4j
@RequiredArgsConstructor
public class DukascopyTickSearch extends BaseDukascopySearch {
    private final Validator validator;
    private final DukascopyCache cache;
    private final DukascopyPathGenerator pathGenerator;

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
                 paths.get(0),
                 paths.get(paths.size() - 1));
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
