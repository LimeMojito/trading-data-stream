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
import com.limemojito.trading.model.tick.Tick;
import com.limemojito.trading.model.tick.TickVisitor;

import java.io.IOException;
import java.time.Instant;

/**
 * Abstraction over search providers that fetch ticks and aggregate them into bars.
 * <p>
 * All bar streams are returned in chronological order (oldest to newest), e.g. 2010 &lt; 2018.
 */
public interface TradingSearch {

    /**
     * Sets the limit of searching.
     *
     * @param theBeginningOfTime past this point searches will end data.
     */
    void setTheBeginningOfTime(Instant theBeginningOfTime);

    /**
     * Gets the limit of searching.
     *
     * @return The point where searches past will stop
     */
    Instant getTheBeginningOfTime();

    /**
     * A cache usage description
     *
     * @return A string representing cache usage.
     */
    String cacheStats();

    /**
     * Retrieve a steam of ticks.
     *
     * @param symbol    Symbol to search on.
     * @param startTime Time to begin search at
     * @param endTime   Time to end search at (exclusive)
     * @return Tick data matching the search request.
     * @throws IOException              on a data failure.
     * @throws IllegalArgumentException if the start time is &lt; the beginningOfTime.
     * @see #getTheBeginningOfTime()
     */
    default TradingInputStream<Tick> search(String symbol,
                                            Instant startTime,
                                            Instant endTime) throws IOException {
        return search(symbol, startTime, endTime, TickVisitor.NO_VISITOR);
    }

    /**
     * Retrieve a steam of ticks.
     *
     * @param symbol      Symbol to search on.
     * @param startTime   Time to begin search at
     * @param endTime     Time to end search at (exclusive)
     * @param tickVisitor Visitor to apply as each tick is found.
     * @return Tick data matching the search request.
     * @throws IOException              on a data failure.
     * @throws IllegalArgumentException if the start time is &lt; the beginningOfTime.
     * @see #getTheBeginningOfTime()
     */
    TradingInputStream<Tick> search(String symbol,
                                    Instant startTime,
                                    Instant endTime,
                                    TickVisitor tickVisitor) throws IOException;

    /**
     * Retrieve a steam of bars by aggregating ticks.
     *
     * @param symbol    Symbol to search on.
     * @param period    Period to aggregate ticks to.
     * @param startTime Time to begin search at
     * @param endTime   Time to end search at (exclusive)
     * @return Bar data matching the search request.
     * @throws IOException              on a data failure.
     * @throws IllegalArgumentException if the start time is &lt; the beginningOfTime.
     * @see #getTheBeginningOfTime()
     */
    default TradingInputStream<Bar> aggregateFromTicks(String symbol,
                                                       Bar.Period period,
                                                       Instant startTime,
                                                       Instant endTime) throws IOException {
        return aggregateFromTicks(symbol, period, startTime, endTime, BarVisitor.NO_VISITOR);
    }

    /**
     * Retrieve a steam of bars by aggregating ticks.
     *
     * @param symbol     Symbol to search on.
     * @param period     Period to aggregate ticks to.
     * @param startTime  Time to begin search at
     * @param endTime    Time to end search at (exclusive)
     * @param barVisitor Visitor to apply as each bar is formed.
     * @return Bar data matching the search request.
     * @throws IOException              on a data failure.
     * @throws IllegalArgumentException if the start time is &lt; the beginningOfTime.
     * @see #getTheBeginningOfTime()
     */
    TradingInputStream<Bar> aggregateFromTicks(String symbol,
                                               Bar.Period period,
                                               Instant startTime,
                                               Instant endTime,
                                               BarVisitor barVisitor) throws IOException;

    /**
     * Retrieve a steam of bars by aggregating ticks, limited count backwards in time.
     *
     * @param symbol         Symbol to search on.
     * @param period         Period to aggregate ticks to.
     * @param barCountBefore Number of bars to find before the end time.
     * @param endTime        The time that the bar start instants must be before (exclusive).
     * @return Bar data matching the search request or best effort before the end of time.
     * @throws IOException on a data failure.
     * @see #getTheBeginningOfTime()
     */
    default TradingInputStream<Bar> aggregateFromTicks(String symbol,
                                                       Bar.Period period,
                                                       int barCountBefore,
                                                       Instant endTime) throws IOException {
        return aggregateFromTicks(symbol,
                                  period,
                                  barCountBefore,
                                  endTime,
                                  BarVisitor.NO_VISITOR);
    }

    /**
     * Retrieve a steam of bars by aggregating ticks, limited count.
     *
     * @param symbol         Symbol to search on.
     * @param period         Period to aggregate ticks to.
     * @param barCountBefore Number of bars to find before the end time.
     * @param endTime        The time that the bar start instants must be before (exclusive).
     * @param barVisitor     Visitor to apply as each bar is formed.
     * @return Bar data matching the search request or best effort before the end of time.
     * @throws IOException on a data failure.
     * @see #getTheBeginningOfTime()
     */
    default TradingInputStream<Bar> aggregateFromTicks(String symbol,
                                                       Bar.Period period,
                                                       int barCountBefore,
                                                       Instant endTime,
                                                       BarVisitor barVisitor) throws IOException {
        return TradingInputStreamBackwardsExtender.extend(symbol,
                                                          period,
                                                          barCountBefore,
                                                          endTime,
                                                          barVisitor,
                                                          this);
    }

    /**
     * Retrieve a steam of bars by aggregating ticks, limited count forwards in time.
     *
     * @param symbol        Symbol to search on.
     * @param period        Period to aggregate ticks to.
     * @param startTime     The time that the bar start instants must be after (inclusive).
     * @param barCountAfter Number of bars to find before the end time.
     * @return Bar data matching the search request or best effort before the current time.
     * @throws IOException on a data failure.
     */
    default TradingInputStream<Bar> aggregateFromTicks(String symbol,
                                                       Bar.Period period,
                                                       Instant startTime,
                                                       int barCountAfter) throws IOException {
        return aggregateFromTicks(symbol,
                                  period,
                                  startTime,
                                  barCountAfter,
                                  BarVisitor.NO_VISITOR);
    }

    /**
     * Retrieve a steam of bars by aggregating ticks, limited count forwards in time.
     *
     * @param symbol        Symbol to search on.
     * @param period        Period to aggregate ticks to.
     * @param startTime     The time that the bar start instants must be after (inclusive).
     * @param barCountAfter Number of bars to find before the end time.
     * @param barVisitor    Visitor to apply as each bar is formed.
     * @return Bar data matching the search request or best effort before the current time.
     * @throws IOException on a data failure.
     */
    default TradingInputStream<Bar> aggregateFromTicks(String symbol,
                                                       Bar.Period period,
                                                       Instant startTime,
                                                       int barCountAfter,
                                                       BarVisitor barVisitor) throws IOException {
        return TradingInputStreamForwardsExtender.extend(symbol,
                                                         period,
                                                         startTime,
                                                         barCountAfter,
                                                         barVisitor,
                                                         this);
    }
}
