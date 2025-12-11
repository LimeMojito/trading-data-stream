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
import com.limemojito.trading.model.bar.Bar.Period;

import java.time.Instant;

/**
 * Common criteria contract for Dukascopy data retrieval.
 * <p>
 * Implementations encapsulate a symbol and a time window, and may add
 * additional fields such as bar {@link Bar.Period}. This interface also
 * provides a small set of static utility methods to validate and normalize
 * the provided instants so that downstream queries align with bar boundaries
 * and inclusive end-of-second semantics used by caches and data sources.
 */
public interface Criteria {
    /**
     * Validates that {@code after} is not before {@code start}.
     *
     * @param start the expected start instant
     * @param after the expected end instant (must be equal to or after {@code start})
     * @throws IllegalArgumentException if {@code after} is before {@code start}
     */
    static void assertBeforeStart(Instant start, Instant after) {
        if (start.isAfter(after)) {
            throw new IllegalArgumentException(String.format("Instant %s must be before %s", start, after));
        }
    }

    /**
     * Rounds an end instant up to the end of the bar that contains it.
     * <p>
     * The logic advances the instant by one bar duration, rounds it to the bar start,
     * then subtracts one second and expands to the very last nanosecond of that second
     * so that ranges are inclusive to the end-of-second.
     *
     * @param period the bar period whose boundaries are used
     * @param end    the requested end instant (inclusive)
     * @return an instant aligned to the inclusive end of the bar containing {@code end}
     */
    static Instant roundEndInstant(Period period, Instant end) {
        Instant updatedEnd = period.round(end.plus(period.getDuration()));
        // if 12:45:33 we need to expand to cover the end of a second.
        updatedEnd = roundEndDateSecond(updatedEnd.minusSeconds(1));
        return updatedEnd;
    }

    /**
     * Ensures an instant represents the last nanosecond of its second.
     * <p>
     * If {@code updatedEnd} falls exactly on a second boundary (nanoseconds == 0),
     * it is shifted forward by one second and then reduced by one nanosecond to
     * make the instant inclusive to the very end of the previous second. Otherwise
     * the original instant is returned unchanged.
     *
     * @param updatedEnd the instant to normalize
     * @return the same instant if not on a boundary, otherwise the last nanosecond of the previous second
     */
    static Instant roundEndDateSecond(Instant updatedEnd) {
        return updatedEnd.getNano() == 0
                ? updatedEnd.plusSeconds(1).minusNanos(1)
                : updatedEnd;
    }

    /**
     * Rounds a start instant down to the start of the bar that contains it.
     *
     * @param period the bar period whose boundaries are used
     * @param start  the requested start instant
     * @return the bar-aligned start instant
     */
    static Instant roundStart(Period period, Instant start) {
        return period.round(start);
    }

    /**
     * The instrument symbol for the requested data (e.g., {@code "EURUSD"}).
     *
     * @return the instrument symbol
     */
    String getSymbol();

    /**
     * The inclusive start instant of the time window for the request.
     *
     * @return start instant (inclusive)
     */
    Instant getStart();

    /**
     * The inclusive end instant of the time window for the request.
     *
     * @return end instant (inclusive)
     */
    Instant getEnd();
}
