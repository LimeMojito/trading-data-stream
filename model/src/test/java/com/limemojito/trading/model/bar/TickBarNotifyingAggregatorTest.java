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

import com.limemojito.trading.model.ModelPrototype;
import com.limemojito.trading.model.TickDataLoader;
import com.limemojito.trading.model.tick.dukascopy.DukascopyUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static com.limemojito.trading.model.StreamData.REALTIME_UUID;
import static com.limemojito.trading.model.StreamData.StreamSource.Historical;
import static com.limemojito.trading.model.bar.Bar.Period.D1;
import static com.limemojito.trading.model.bar.Bar.Period.M5;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class TickBarNotifyingAggregatorTest {
    private final Bar.Period aggPeriod = M5;

    @Mock
    private TickBarNotifyingAggregator.BarNotifier barSender;
    @Captor
    private ArgumentCaptor<Bar> barCaptor;

    private TickBarNotifyingAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new TickBarNotifyingAggregator(DukascopyUtils.setupValidator(), barSender, aggPeriod);
    }

    @Test
    public void shouldAggregateOneHourTicksTo12Bars() throws Exception {
        performLoad("EURUSD/2018/06/05/05h_ticks.bi5");

        final List<Bar> values = barCaptor.getAllValues();
        Assertions.assertThat(values).hasSize(12);
        assert5amBar(values.get(0));
    }

    @Test
    public void shouldAggregate2HourTicksTo24Bars() throws Exception {
        performLoad("EURUSD/2018/06/05/05h_ticks.bi5", "EURUSD/2018/06/05/06h_ticks.bi5");

        final List<Bar> bars = barCaptor.getAllValues();
        Assertions.assertThat(bars).hasSize(24);
        assert5amBar(bars.get(0));
        assert6amBar(bars.get(12));
    }

    @Test
    public void shouldLoad6AmHourOk() throws Exception {
        performLoad("EURUSD/2018/06/05/06h_ticks.bi5");

        final List<Bar> bars = barCaptor.getAllValues();
        Assertions.assertThat(bars).hasSize(12);
        assert6amBar(bars.get(0));
    }

    @Test
    public void shouldLoadThreeHoursOk() throws Exception {
        performLoad("EURUSD/2018/06/05/05h_ticks.bi5",
                    "EURUSD/2018/06/05/06h_ticks.bi5",
                    "EURUSD/2018/06/05/07h_ticks.bi5");

        final List<Bar> bars = barCaptor.getAllValues();
        Assertions.assertThat(bars).hasSize(36);
        assert5amBar(bars.get(0));
        assert6amBar(bars.get(12));
        assert7amBar(bars.get(24));
    }

    @Test
    public void shouldCoverDefaultMethod() {
        final TickBarNotifyingAggregator.BarNotifier notifier = System.out::println;

        notifier.notify(ModelPrototype.createBar(REALTIME_UUID, "EURUSD", D1, System.currentTimeMillis()));
        notifier.flush();
    }

    private void performLoad(String... paths) throws IOException {
        aggregator.loadStart();
        for (String path : paths) {
            TickDataLoader.loadTickData(path).forEach(tick -> aggregator.add(tick));
        }
        aggregator.loadEnd();

        verify(barSender, Mockito.times(paths.length * 12)).notify(barCaptor.capture());
        verify(barSender).flush();
    }

    private void assert7amBar(Bar bar) {
        assertBar(bar, 1530774000000L, 116969, 117019, 117034, 116933);
    }

    private void assert6amBar(Bar bar) {
        assertBar(bar, 1530770400000L, 116937, 116928, 117018, 116909);
    }

    private void assert5amBar(Bar bar) {
        assertBar(bar, 1530766800000L, 116568, 116545, 116571, 116535);
    }

    private void assertBar(Bar bar,
                           long epochInMilliseconds,
                           int open,
                           int close,
                           int high,
                           int low) {
        final String symbol = "EURUSD";
        Assertions.assertThat(bar).usingRecursiveComparison()
                  .isEqualTo(Bar.builder()
                                .streamId(REALTIME_UUID)
                                .symbol(symbol)
                                .period(aggPeriod)
                                .startMillisecondsUtc(epochInMilliseconds)
                                .open(open)
                                .close(close)
                                .high(high)
                                .low(low)
                                .source(Historical)
                                .build());
    }
}
