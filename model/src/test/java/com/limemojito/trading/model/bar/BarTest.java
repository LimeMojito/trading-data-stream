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

import com.limemojito.json.JsonLoader;
import com.limemojito.json.JsonMapperPrototype;
import com.limemojito.test.JsonAsserter;
import com.limemojito.trading.model.ModelPrototype;
import com.limemojito.trading.model.UtcTimeUtils;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import static com.limemojito.trading.model.StreamData.REALTIME_UUID;
import static com.limemojito.trading.model.StreamData.StreamSource.Historical;
import static com.limemojito.trading.model.StreamData.StreamType.Backtest;
import static com.limemojito.trading.model.StreamData.StreamType.Realtime;
import static com.limemojito.trading.model.bar.Bar.Period.D1;
import static com.limemojito.trading.model.bar.Bar.Period.H1;
import static com.limemojito.trading.model.bar.Bar.Period.M10;
import static com.limemojito.trading.model.bar.Bar.Period.M15;
import static com.limemojito.trading.model.bar.Bar.Period.M5;
import static org.assertj.core.api.Assertions.assertThat;

public class BarTest {
    static void assertBar(Bar bar,
                          String startInstantString,
                          String endInstantString,
                          String symbol,
                          Bar.Period period,
                          int expectedOpen,
                          int expectedHigh,
                          int expectedLow,
                          int expectedClose) {
        assertThat(bar.getStartInstant()).isEqualTo(startInstantString);
        assertThat(bar.getEndInstant()).isEqualTo(endInstantString);
        assertThat(bar.getSymbol()).isEqualTo(symbol);
        assertThat(bar.getSource()).isEqualTo(Historical);
        assertThat(bar.getPeriod()).isEqualTo(period);
        assertThat(bar.getOpen()).isEqualTo(expectedOpen);
        assertThat(bar.getHigh()).isEqualTo(expectedHigh);
        assertThat(bar.getLow()).isEqualTo(expectedLow);
        assertThat(bar.getClose()).isEqualTo(expectedClose);
    }

    @Test
    public void barShouldBeJsonable() {
        final Bar bar = ModelPrototype.createBar(REALTIME_UUID, "EURUSD", H1, 1528174800000L);
        assertThat(bar.getEndMillisecondsUtc()).isEqualTo(bar.getStartMillisecondsUtc() + H1.getDurationMilliseconds() - 1L);

        assertThat(bar.getStartDateTimeUtc()).isEqualTo(UtcTimeUtils.toLocalDateTimeUtc(bar.getStartMillisecondsUtc()));
        assertThat(bar.getEndDateTimeUtc()).isEqualTo(UtcTimeUtils.toLocalDateTimeUtc(bar.getEndMillisecondsUtc()));

        assertThat(bar.getStartInstant()).isEqualTo(UtcTimeUtils.toInstant(bar.getStartMillisecondsUtc()));
        assertThat(bar.getEndInstant()).isEqualTo(UtcTimeUtils.toInstant(bar.getEndMillisecondsUtc()));

        JsonAsserter.assertSerializeDeserialize(bar);
    }

    @Test
    public void shouldBeStableJson() {
        JsonMapper objectMapper = JsonMapperPrototype.buildBootLikeMapper();
        JsonLoader loader = new JsonLoader(objectMapper);

        final Bar bar = ModelPrototype.createBar(REALTIME_UUID, "EURUSD", H1, 1528174800000L);
        Bar version1 = loader.loadFrom("/model/bar-1.0.json", Bar.class);
        assertThat(bar).usingRecursiveComparison().isEqualTo(version1);
    }

    @Test
    public void shouldHaveComparisonWithStreamIncluded() {
        final Bar m5 = ModelPrototype.createBar(REALTIME_UUID, "EURUSD", M5, 1528174800000L);
        final Bar other = ModelPrototype.createBar(UUID.randomUUID(),
                                                   m5.getSymbol(),
                                                   m5.getPeriod(),
                                                   m5.getStartMillisecondsUtc());

        assertThat(m5.getPartitionKey()).isEqualTo("00000000-0000-0000-0000-000000000000-EURUSD-M5");

        // live is > backtest
        assertThat(m5.getStreamType()).isEqualTo(Realtime);
        assertThat(m5.compareTo(other)).isGreaterThan(0);
        assertThat(m5.isInSameStream(other)).isFalse();
        assertThat(m5.isInSameStream(m5)).isTrue();
        assertThat(m5.within(other)).isFalse();
        assertThat(m5.surrounds(other)).isFalse();
        assertThat(other.surrounds(m5)).isFalse();
        assertThat(other.getStreamType()).isEqualTo(Backtest);
    }

    @Test
    public void shouldComputeBarCount() {
        assertThat(Bar.barsIn(M5, Duration.ofMinutes(20))).isEqualTo(4);
        assertThat(Bar.barsIn(M5, Duration.ofMinutes(22))).isEqualTo(4);
        assertThat(Bar.barsIn(M5, Duration.ofMinutes(25))).isEqualTo(5);
        assertThat(Bar.barsIn(M5, Duration.ofSeconds(25))).isEqualTo(0);
        assertThat(Bar.barsIn(M5, Duration.ofSeconds(5 * 60))).isEqualTo(1);
        assertThat(Bar.barsIn(M5, Duration.ofSeconds(5 * 62))).isEqualTo(1);
        assertThat(Bar.barsIn(M10, Duration.ofMinutes(20))).isEqualTo(2);
    }

    @Test
    public void shouldComputeBarCountsWithIntervals() {
        performBarsInTimeTest(M5, "2018-07-06T12:00:00Z", "2018-07-06T12:05:00Z", 1);
        performBarsInTimeTest(M5, "2018-07-06T12:00:00Z", "2018-07-06T12:15:00Z", 3);
        performBarsInTimeTest(M15, "2018-07-06T12:00:00Z", "2018-07-06T12:15:00Z", 1);
        performBarsInTimeTest(D1, "2018-07-06T12:00:00Z", "2018-07-06T12:15:00Z", 0);
        performBarsInTimeTest(D1, "2018-07-06T00:00:00Z", "2018-07-07T00:00:00Z", 1);
    }

    @Test
    public void shouldComputePeriodComparisonCounts() {
        assertThat(M10.periodsIn(M5)).isEqualTo(0);
        assertThat(M5.periodsIn(M10)).isEqualTo(2);
        assertThat(M15.periodsIn(H1)).isEqualTo(4);
        assertThat(H1.periodsIn(M15)).isEqualTo(0);
    }

    @Test
    public void shouldTestPeriodsInTimeComparison() {
        assertThat(M10.periodsIn(LocalTime.of(1, 0, 0), LocalTime.of(1, 10, 0))).isEqualTo(1);
        assertThat(M10.periodsIn(LocalTime.of(1, 0, 0), LocalTime.of(1, 20, 0))).isEqualTo(2);
        assertThat(M10.periodsIn(LocalTime.of(1, 10, 0), LocalTime.of(0, 20, 0))).isEqualTo(0);
    }

    @SuppressWarnings("EqualsWithItself")
    @Test
    public void shouldBeWithinAndComparable() {
        final long startMsUtc = 1528174800000L;
        final Bar h1 = ModelPrototype.createBar(REALTIME_UUID, "EURUSD", H1, startMsUtc);
        final Bar h1Future = ModelPrototype.createBar(REALTIME_UUID, "EURUSD", H1, startMsUtc * 4);

        final Bar m5 = ModelPrototype.createBar(REALTIME_UUID, "EURUSD", M5, startMsUtc);
        final Bar m5Middle = ModelPrototype.createBar(REALTIME_UUID,
                                                      "EURUSD",
                                                      M5,
                                                      startMsUtc + (M5.getDurationMilliseconds() * 4));

        // based on time
        assertThat(m5.compareTo(m5)).isEqualTo(0);
        assertThat(m5.compareTo(m5Middle)).isLessThan(0);
        assertThat(m5Middle.compareTo(m5)).isGreaterThan(0);

        // based on period size
        assertThat(m5.compareTo(h1)).isLessThan(0);
        assertThat(m5.compareTo(h1Future)).isLessThan(0);
        assertThat(h1Future.compareTo(m5)).isGreaterThan(0);

        assertThat(m5.within(h1)).isTrue();
        assertThat(m5.surrounds(h1)).isFalse();
        assertThat(m5.within(h1Future)).isFalse();

        assertThat(h1.within(m5)).isFalse();
        assertThat(h1.surrounds(m5)).isTrue();
        assertThat(h1Future.surrounds(m5)).isFalse();

        assertThat(m5Middle.within(m5)).isFalse();
        assertThat(m5Middle.surrounds(m5)).isFalse();

        assertThat(m5Middle.within(h1)).isTrue();
        assertThat(h1.surrounds(m5Middle)).isTrue();
        assertThat(h1Future.surrounds(m5Middle)).isFalse();

        assertThat(h1.within(m5)).isFalse();
        assertThat(h1Future.within(m5)).isFalse();
    }

    private void performBarsInTimeTest(Bar.Period period, String start, String end, int expected) {
        assertThat(Bar.barsIn(period, Instant.parse(start), Instant.parse(end))).isEqualTo(expected);
    }
}
