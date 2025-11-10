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

package com.limemojito.trading.model.tick.dukascopy;

import com.limemojito.trading.model.tick.dukascopy.criteria.BarCriteria;
import com.limemojito.trading.model.tick.dukascopy.criteria.Criteria;
import com.limemojito.trading.model.tick.dukascopy.criteria.TickCriteria;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.limemojito.trading.model.bar.Bar.Period.M30;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CriteriaTest {

    private final String symbol = "EURUSD";

    @Test
    public void tickCriteriaShouldAssertStartAndEnd() {
        Instant start = Instant.parse("2020-01-02T00:00:00Z");
        Instant end = Instant.parse("2009-01-02T00:59:59Z");
        assertThatThrownBy(() -> new TickCriteria(symbol, start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Instant 2020-01-02T00:00:00Z must be before 2009-01-02T00:59:59Z");
    }

    @Test
    public void barCriteriaShouldAssertStartAndEnd() {
        Instant start = Instant.parse("2009-01-02T00:59:59Z");
        Instant end = Instant.parse("2020-01-02T00:00:00Z");
        assertThatThrownBy(() -> new BarCriteria(symbol, M30, end, start))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Instant 2020-01-02T00:00:00Z must be before 2009-01-02T00:59:59Z");
    }

    @Test
    public void shouldRoundStartAndEndToPeriod() {
        Instant start = Instant.parse("2009-01-02T00:04:22.33Z");
        Instant end = Instant.parse("2020-01-02T00:28:43.22Z");

        // criteria EXPANDS to include period.
        Criteria criteria = new BarCriteria(symbol, M30, start, end);

        Instant roundedStart = criteria.getStart();
        Instant roundedEnd = criteria.getEnd();

        assertThat(roundedStart).isEqualTo("2009-01-02T00:00:00Z");
        assertThat(roundedEnd).isEqualTo("2020-01-02T00:29:59.999999999Z");
    }
}
