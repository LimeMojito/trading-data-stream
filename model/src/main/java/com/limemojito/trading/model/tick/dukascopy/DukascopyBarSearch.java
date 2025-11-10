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

@RequiredArgsConstructor
@Slf4j
public class DukascopyBarSearch extends BaseDukascopySearch {
    private final BarCache cache;
    private final DukascopyPathGenerator pathGenerator;

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
