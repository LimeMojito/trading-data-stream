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

import com.limemojito.trading.model.UtcTimeUtils;
import com.limemojito.trading.model.tick.Tick;
import com.limemojito.trading.model.tick.dukascopy.DukascopyTickInputStream;
import com.limemojito.trading.model.tick.dukascopy.DukascopyUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.UUID;

import static com.limemojito.trading.model.ModelPrototype.createTick;
import static com.limemojito.trading.model.StreamData.REALTIME_UUID;
import static com.limemojito.trading.model.StreamData.StreamSource.Historical;
import static com.limemojito.trading.model.StreamData.StreamSource.Live;
import static com.limemojito.trading.model.bar.Bar.Period.H1;
import static com.limemojito.trading.model.bar.Bar.Period.M5;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BarTickStreamAggregatorTest {

    private final long startMillisecondsUtc = Instant.parse("2019-02-02T05:16:32Z").toEpochMilli();
    private final String symbol = "EURUSD";
    private BarTickStreamAggregator barAggregator;

    @BeforeEach
    void setUp() {
        barAggregator = new BarTickStreamAggregator(DukascopyUtils.setupValidator(), REALTIME_UUID, symbol, startMillisecondsUtc, M5);
    }

    @Test
    public void shouldHaveUtcConversionFunctions() {
        assertThat(barAggregator.getStartDateInstant()).isEqualTo("2019-02-02T05:15:00Z");
        assertThat(barAggregator.getEndMillisecondsUtc()).isEqualTo(barAggregator.getStartMillisecondsUtc() + M5.getDurationMilliseconds() - 1);
        assertThat(barAggregator.getEndDateInstant()).isEqualTo(UtcTimeUtils.toInstant(barAggregator.getEndMillisecondsUtc()));
    }

    @Test
    public void shouldNotAllowOtherStreamTick() {
        final UUID backtestId = UUID.randomUUID();
        final Tick tick = createTick(backtestId, symbol, startMillisecondsUtc + 5000, 116928, Live);
        assertThatThrownBy(() -> barAggregator.add(tick)).isInstanceOf(ConstraintViolationException.class)
                                                         .hasMessage(
                                                                 "Tick EURUSD 2019-02-02T05:16:37Z (1549084597000) is not part of stream 00000000-0000-0000-0000-000000000000");
    }

    @Test
    public void shouldNotAllowDifferentSymbolTick() {
        final Tick tick = createTick("AUDUSD", startMillisecondsUtc + 5600, 116928, Historical);
        assertThatThrownBy(() -> barAggregator.add(tick)).isInstanceOf(ConstraintViolationException.class)
                                                         .hasMessage(
                                                                 "Tick AUDUSD 2019-02-02T05:16:37.600Z (1549084597600) is not matching bar symbol EURUSD");
    }

    @Test
    public void shouldNotAllowEarlyTick() {
        final long earlyTime = Instant.parse("2010-04-05T09:12:13Z").toEpochMilli();
        final Tick tick = createTick(symbol, earlyTime, 116928, Historical);
        assertThatThrownBy(() -> barAggregator.add(tick)).isInstanceOf(ConstraintViolationException.class)
                                                         .hasMessage(
                                                                 "Tick EURUSD 2010-04-05T09:12:13Z (1270458733000) is before start of bar 2019-02-02T05:15:00Z (1549084500000)");
    }

    @Test
    public void shouldNotAllowLateTick() {
        final long late = Instant.parse("2019-02-02T05:20:02Z").toEpochMilli();
        final Tick tick = createTick(symbol, late, 116928, Historical);
        assertThatThrownBy(() -> barAggregator.add(tick)).isInstanceOf(ConstraintViolationException.class)
                                                         .hasMessage(
                                                                 "Tick EURUSD 2019-02-02T05:20:02Z (1549084802000) is past end of bar 2019-02-02T05:19:59.999Z (1549084799999)");
    }

    @Test
    public void shouldRoundStartTime() {
        BarTickStreamAggregator aggregator = new BarTickStreamAggregator(DukascopyUtils.setupValidator(),
                                                                         REALTIME_UUID,
                                                                         symbol,
                                                                         Instant.parse("2020-01-05T11:54:43Z"),
                                                                         M5);

        assertThat(aggregator.getStartDateInstant()).isEqualTo("2020-01-05T11:50:00Z");
        assertThat(aggregator.getEndDateInstant()).isEqualTo("2020-01-05T11:54:59.999Z");
    }

    @Test
    public void shouldAggregate() {
        final int open = 116928;
        final int high = 117003;
        final int low = 116620;
        final int close = 116628;
        final long startMs = barAggregator.getStartMillisecondsUtc();
        barAggregator.add(createTick(symbol, startMs + 10000, open, Historical));
        barAggregator.add(createTick(symbol, startMs + 20000, high, Live));
        barAggregator.add(createTick(symbol, startMs + 30000, low, Historical));
        barAggregator.add(createTick(symbol, startMs + 40000, close, Live));
        Bar bar = barAggregator.toBar();

        assertThat(bar.getStartMillisecondsUtc()).isEqualTo(barAggregator.getStartMillisecondsUtc());
        assertThat(bar.getStartInstant()).isEqualTo("2019-02-02T05:15:00Z");
        assertThat(bar.getStartDateTimeUtc()).isEqualTo("2019-02-02T05:15:00");
        assertThat(bar.getEndMillisecondsUtc()).isEqualTo(barAggregator.getEndMillisecondsUtc());
        assertThat(bar.getEndInstant()).isEqualTo("2019-02-02T05:19:59.999Z");
        assertThat(bar.getEndDateTimeUtc()).isEqualTo("2019-02-02T05:19:59.999");
        assertThat(bar.getSymbol()).isEqualTo(symbol);
        assertThat(bar.getPeriod()).isEqualTo(M5);
        // historical beats live
        assertThat(bar.getSource()).isEqualTo(Historical);

        assertThat(bar.getOpen()).isEqualTo(open);
        assertThat(bar.getClose()).isEqualTo(close);
        assertThat(bar.getHigh()).isEqualTo(high);
        assertThat(bar.getLow()).isEqualTo(low);
    }

    @Test
    public void shouldAggregateOneHourFromDukascopy() throws Exception {
        // expected numbers are from excel analysis of dukascopy csv conversion.
        final int expectedOpen = 116568;
        final int expectedLow = 116500;
        final int expectedHigh = 116939;
        final int expectedClose = 116935;

        final String path = "EURUSD/2018/06/05/05h_ticks.bi5";
        final long startMillisecondsUtc = 1530766800000L;

        final Validator validator = DukascopyUtils.setupValidator();
        final Bar.Period period = H1;
        final String symbol = "EURUSD";
        BarTickStreamAggregator aggregator = new BarTickStreamAggregator(validator,
                                                                         REALTIME_UUID,
                                                                         symbol,
                                                                         startMillisecondsUtc,
                                                                         period);
        try (DukascopyTickInputStream inputStream = new DukascopyTickInputStream(validator,
                                                                                 path,
                                                                                 getClass().getResourceAsStream("/" + path))) {
            while (inputStream.hasNext()) {
                Tick tick = inputStream.next();
                aggregator.add(tick);
            }
        }

        Bar h1 = aggregator.toBar();

        BarTest.assertBar(h1,
                          "2018-07-05T05:00:00Z",
                          "2018-07-05T05:59:59.999Z",
                          symbol,
                          period,
                          expectedOpen,
                          expectedHigh,
                          expectedLow,
                          expectedClose
        );
    }
}
