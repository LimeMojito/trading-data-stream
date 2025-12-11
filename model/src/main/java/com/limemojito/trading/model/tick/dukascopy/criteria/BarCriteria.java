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

package com.limemojito.trading.model.tick.dukascopy.criteria;

import com.limemojito.trading.model.bar.Bar;
import lombok.Value;

import java.time.Duration;
import java.time.Instant;

import static com.limemojito.trading.model.tick.dukascopy.criteria.Criteria.assertBeforeStart;
import static java.time.temporal.ChronoUnit.DAYS;


/**
 * Criteria for retrieving OHLC bar data from the Dukascopy data source.
 * <p>
 * A {@code BarCriteria} encapsulates the symbol, bar {@link Bar.Period period}, and a time window
 * defined by {@code start} and {@code end}. The constructor normalizes the provided instants to
 * align with bar boundaries so that downstream queries operate on whole bars only. In addition, an
 * inclusive day range is computed to facilitate day-by-day processing (useful for caches that are
 * organized by day).
 */
@Value
@SuppressWarnings("RedundantModifiersValueLombok")
public class BarCriteria implements Criteria {
    /**
     * Creates a new set of criteria for bar retrieval.
     * <p>
     * The {@code start} and {@code end} instants are validated (end must not be before start) and
     * then rounded to bar boundaries as follows:
     * <ul>
     *   <li>{@code start} is rounded down to the start of the bar that contains it.</li>
     *   <li>{@code end} is rounded up to the end instant of the bar that contains it.</li>
     * </ul>
     * Day-level boundaries are also computed and stored for efficient per-day iteration.
     *
     * @param symbol the instrument symbol (e.g., "EURUSD")
     * @param period the bar period (granularity) to request
     * @param start  the requested start instant (inclusive before rounding)
     * @param end    the requested end instant (inclusive before rounding)
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     */
    public BarCriteria(String symbol, Bar.Period period, Instant start, Instant end) {
        this.symbol = symbol;
        this.period = period;
        assertBeforeStart(start, end);
        this.start = Criteria.roundStart(period, start);
        this.end = Criteria.roundEndInstant(period, end);
        this.dayStart = start.truncatedTo(DAYS);
        this.dayEnd = end.plus(1, DAYS).truncatedTo(DAYS).minusNanos(1);
        this.numDays = (int) Duration.between(dayStart, dayEnd).toDaysPart() + 1;
    }

    /**
     * Returns the start instant (inclusive) of the {@code i}-th day within the criteria's day range.
     * Day {@code 0} corresponds to the day of the provided {@code start} instant.
     *
     * @param incrementDays zero-based day index within the inclusive day range
     * @return the start instant of the {@code i}-th day (at 00:00:00.000...)
     * @throws IndexOutOfBoundsException if {@code i} is negative or greater than or equal to {@code numDays}
     */
    public Instant getDayStart(int incrementDays) {
        return dayStart.plus(incrementDays, DAYS);
    }

    /**
     * Returns the end instant (inclusive) of the {@code i}-th day within the criteria's day range.
     * Day {@code 0} corresponds to the day of the provided {@code start} instant.
     *
     * @param incrementDays zero-based day index within the inclusive day range
     * @return the inclusive end instant of the {@code i}-th day (at 23:59:59.999999999)
     * @throws IndexOutOfBoundsException if {@code i} is negative or greater than or equal to {@code numDays}
     */
    public Instant getDayEnd(int incrementDays) {
        return dayStart.plus(incrementDays + 1, DAYS).minusNanos(1);
    }

    private final int numDays;
    private final String symbol;
    private final Bar.Period period;
    private final Instant start;
    private final Instant end;
    private final Instant dayStart;
    private final Instant dayEnd;
}
