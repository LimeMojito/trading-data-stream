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

package com.limemojito.trading.model.bar;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static com.limemojito.trading.model.bar.Bar.Period.D1;
import static com.limemojito.trading.model.bar.Bar.Period.H1;
import static com.limemojito.trading.model.bar.Bar.Period.M10;
import static com.limemojito.trading.model.bar.Bar.Period.M15;
import static com.limemojito.trading.model.bar.Bar.Period.M5;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

public class PeriodRoundingTest {

    @Test
    public void shouldNormalizeM5AsSupplied() {
        normalise("2018-01-01T13:00:00", M5, "2018-01-01T13:00:00Z");
    }

    @Test
    public void shouldNormalizeToM5Boundary() {
        normalise("2018-01-01T13:04:32", M5, "2018-01-01T13:00:00Z");
    }

    @Test
    public void shouldNormalizeD1ToStartBoundary() {
        normalise("2018-01-01T13:04:32", D1, "2018-01-01T00:00:00Z");
        normalise("2018-01-01T23:59:59", D1, "2018-01-01T00:00:00Z");
        normalise("2018-01-01T00:00:00", D1, "2018-01-01T00:00:00Z");
    }

    @Test
    public void shouldRoundM10() {
        normalise("2018-01-01T23:09:22", M10, "2018-01-01T23:00:00Z");
    }

    @Test
    public void shouldRoundH1() {
        normalise("2018-01-01T23:45:00", H1, "2018-01-01T23:00:00Z");
    }

    @Test
    public void shouldRoundM15T() {
        normalise("2018-01-01T23:14:00", M15, "2018-01-01T23:00:00Z");
    }

    private void normalise(String queryTime, Bar.Period queryPeriod, String m5RangeStart) {
        final Instant startTime = LocalDateTime.parse(queryTime).toInstant(UTC);
        final Instant rounded = queryPeriod.round(startTime);
        assertThat(rounded).isEqualTo(Instant.parse(m5RangeStart));
    }
}
