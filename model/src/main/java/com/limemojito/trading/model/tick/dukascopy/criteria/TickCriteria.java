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

import lombok.Value;

import java.time.Instant;

import static com.limemojito.trading.model.tick.dukascopy.criteria.Criteria.assertBeforeStart;

/**
 * Criteria for retrieving raw tick data from Dukascopy-backed sources.
 * <p>
 * This immutable value object encapsulates an instrument symbol and a time window
 * defined by inclusive {@link #getStart()} and {@link #getEnd()} instants. The constructor
 * enforces a valid range (end not before start) and normalizes the end instant
 * to be inclusive to the last nanosecond of its second using
 * {@link Criteria#roundEndDateSecond(Instant)}.
 * <p>
 * The generated accessors are provided by Lombok's {@link Value} and documented via
 * field-level Javadoc.
 */
@Value
@SuppressWarnings("RedundantModifiersValueLombok")
public class TickCriteria implements Criteria {
    /**
     * Creates tick retrieval criteria for the given symbol and time window.
     * <p>
     * Validation ensures {@code end} is not before {@code start}; the {@code end}
     * is then normalized to be inclusive to the end of its second.
     *
     * @param symbol the instrument symbol (e.g., {@code "EURUSD"})
     * @param start  the inclusive start instant of the requested window
     * @param end    the inclusive end instant of the requested window; will be
     *               normalized via {@link Criteria#roundEndDateSecond(Instant)}
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     */
    public TickCriteria(String symbol, Instant start, Instant end) {
        this.symbol = symbol;
        assertBeforeStart(start, end);
        this.start = start;
        this.end = Criteria.roundEndDateSecond(end);
    }

    /**
     * The instrument symbol for which ticks are requested.
     *
     * @return the instrument symbol
     */
    private final String symbol;
    /**
     * The inclusive start instant of the window for tick retrieval.
     *
     * @return the start instant (inclusive)
     */
    private final Instant start;
    /**
     * The inclusive end instant of the window for tick retrieval.
     * The value is normalized to the last nanosecond of its second.
     *
     * @return the end instant (inclusive)
     */
    private final Instant end;
}
