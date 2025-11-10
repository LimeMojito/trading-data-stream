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

package com.limemojito.trading.model;

import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.bar.BarVisitor;
import com.limemojito.trading.model.stream.TradingInputBackwardsSearchStream;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Helper to progressively extend a bar search backwards in time.
 * <p>
 * Creates a {@link TradingInputStream} that repeatedly issues searches for older ranges until the
 * desired number of bars are collected or the configured beginning-of-time boundary is reached.
 */
@Slf4j
public final class TradingInputStreamBackwardsExtender {

    /**
     * Build an input stream that paginates backwards from the given {@code endTime} and aggregates
     * ticks into bars for the supplied period, up to {@code barCountBefore} bars.
     *
     * @param symbol         Symbol to search.
     * @param period         Period to aggregate.
     * @param barCountBefore Number of bars to retrieve before end time.
     * @param endTime        End-instant (exclusive) for the first page.
     * @param barVisitor     Visitor to apply to each bar as it is produced.
     * @param tradingSearch  Search engine to use.
     * @return a bar input stream
     * @throws IOException on an IO failure.
     * @see TradingSearch#aggregateFromTicks(String, Bar.Period, Instant, Instant, BarVisitor)
     */
    public static TradingInputStream<Bar> extend(String symbol,
                                                 Bar.Period period,
                                                 int barCountBefore,
                                                 Instant endTime,
                                                 BarVisitor barVisitor,
                                                 TradingSearch tradingSearch) throws IOException {
        return new TradingInputBackwardsSearchStream<>(barCountBefore,
                                                       new TradingInputBackwardsSearchStream.Search<>() {
                                                           private Instant start;
                                                           private Instant end;

                                                           @Override
                                                           public void sort(List<Bar> data) {
                                                               data.sort(Comparator.comparing(Bar::getStartMillisecondsUtc));
                                                           }

                                                           @Override
                                                           public boolean prepare(int searchCount) {
                                                               final Duration duration = period.getDuration()
                                                                                               .multipliedBy(
                                                                                                       barCountBefore);
                                                               final Instant theBeginningOfTime = tradingSearch.getTheBeginningOfTime();
                                                               start = endTime.minus(duration.multipliedBy(searchCount
                                                                                                           + 1));
                                                               if (start.compareTo(theBeginningOfTime) < 0) {
                                                                   log.warn("Reached the beginning of Time {}",
                                                                            theBeginningOfTime);
                                                                   start = theBeginningOfTime;
                                                               }
                                                               end = endTime.minus(duration.multipliedBy(searchCount))
                                                                            .minusNanos(1);
                                                               return start.equals(theBeginningOfTime);
                                                           }

                                                           @Override
                                                           public TradingInputStream<Bar> perform() throws IOException {
                                                               log.debug("Performing search between {} and {}",
                                                                         start,
                                                                         end);
                                                               return tradingSearch.aggregateFromTicks(symbol,
                                                                                                       period,
                                                                                                       start,
                                                                                                       end,
                                                                                                       barVisitor);
                                                           }
                                                       });
    }
}
