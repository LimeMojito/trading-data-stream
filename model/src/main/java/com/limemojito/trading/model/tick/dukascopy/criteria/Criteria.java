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

import java.time.Instant;

public interface Criteria {
    static void assertBeforeStart(Instant start, Instant after) {
        if (start.isAfter(after)) {
            throw new IllegalArgumentException(String.format("Instant %s must be before %s", start, after));
        }
    }

    static Instant roundEndInstant(Bar.Period period, Instant end) {
        Instant updatedEnd = period.round(end.plus(period.getDuration()));
        // if 12:45:33 we need to expand to cover the end of second.
        updatedEnd = roundEndDateSecond(updatedEnd.minusSeconds(1));
        return updatedEnd;
    }

    static Instant roundEndDateSecond(Instant updatedEnd) {
        return updatedEnd.getNano() == 0
                ? updatedEnd.plusSeconds(1).minusNanos(1)
                : updatedEnd;
    }

    static Instant roundStart(Bar.Period period, Instant start) {
        return period.round(start);
    }

    String getSymbol();

    java.time.Instant getStart();

    java.time.Instant getEnd();
}
