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

@Value
@SuppressWarnings("RedundantModifiersValueLombok")
public class BarCriteria implements Criteria {
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

    public Instant getDayStart(int i) {
        return dayStart.plus(i, DAYS);
    }

    public Instant getDayEnd(int i) {
        return dayStart.plus(i + 1, DAYS).minusNanos(1);
    }

    private final int numDays;
    private final String symbol;
    private final Bar.Period period;
    private final Instant start;
    private final Instant end;
    private final Instant dayStart;
    private final Instant dayEnd;
}
