/*
 * Copyright 2011-2022 Lime Mojito Pty Ltd
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
import com.limemojito.trading.model.bar.BarVisitor;
import com.limemojito.trading.model.bar.TickToBarInputStream;
import com.limemojito.trading.model.tick.Tick;
import com.limemojito.trading.model.tick.TickVisitor;
import com.limemojito.trading.model.tick.dukascopy.criteria.BarCriteria;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.validation.Validator;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

@RequiredArgsConstructor
@Slf4j
public class DukascopyBarSearch extends BaseDukascopySearch  {
    private final Validator validator;
    private final DukascopyCache cache;
    private final DukascopyPathGenerator pathGenerator;
    private final DukascopyTickSearch tickSearch;
    public TradingInputStream<Bar> search(String symbol,
                                          Bar.Period period,
                                          Instant startTime,
                                          Instant endTime,
                                          BarVisitor barVisitor,
                                          TickVisitor tickVisitor) {
        BarCriteria criteria = buildBarCriteria(symbol, period, startTime, endTime);
        log.debug("Forming bar stream for {} {} {} -> {}",
                  criteria.getSymbol(),
                  criteria.getPeriod(),
                  criteria.getStart(),
                  criteria.getEnd());
        final List<TradingInputStream<Bar>> barInputStreams = new LinkedList<>();
        final List<List<String>> groupedPaths = pathGenerator.generatePathsGroupedByDay(symbol, startTime, endTime);
        for (List<String> dayOfPaths : groupedPaths) {
            final TradingInputStream<Tick> dayOfTicks = tickSearch.search(criteria,
                                                                          dayOfPaths,
                                                                          tickVisitor);
            final TradingInputStream<Bar> oneDayOfBarStream = new TickToBarInputStream(validator,
                                                                                       period,
                                                                                       barVisitor,
                                                                                       dayOfTicks);
            barInputStreams.add(oneDayOfBarStream);
        }
        final Predicate<Bar> trimFilter = bar ->
                bar.getStartInstant().compareTo(startTime) >= 0
                        && bar.getStartInstant().compareTo(endTime) <= 0;
        final TradingInputStream<Bar> barStream = TradingInputStream.combine(barInputStreams.iterator(), trimFilter);
        log.info("Returning bar stream for {} {} {} -> {}",
                 criteria.getSymbol(),
                 criteria.getPeriod(),
                 criteria.getStart(),
                 criteria.getEnd());
        return barStream;
    }

    private BarCriteria buildBarCriteria(String symbol, Bar.Period period, Instant startTime, Instant endTime) {
        return new BarCriteria(symbol, period, startTime, endTime);
    }
}
