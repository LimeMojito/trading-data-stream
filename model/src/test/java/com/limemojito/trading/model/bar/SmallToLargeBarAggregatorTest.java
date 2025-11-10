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
import com.limemojito.trading.model.tick.dukascopy.DukascopyUtils;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validator;
import java.util.Collections;
import java.util.List;

import static com.limemojito.trading.model.ModelPrototype.createBar;
import static com.limemojito.trading.model.StreamData.REALTIME_UUID;
import static com.limemojito.trading.model.bar.Bar.Period.D1;
import static com.limemojito.trading.model.bar.Bar.Period.H1;
import static com.limemojito.trading.model.bar.Bar.Period.M10;
import static com.limemojito.trading.model.bar.Bar.Period.M5;
import static com.limemojito.trading.model.bar.BarTest.assertBar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SmallToLargeBarAggregatorTest {

    private static final Validator VALIDATOR = DukascopyUtils.setupValidator();

    @Test
    public void shouldAggregateOneBarsOk() {
        final List<Bar> m5Bars = createBarListDescendingOf(1);

        List<Bar> aggregated1H = new SmallToLargeBarAggregator(VALIDATOR).aggregate(H1, m5Bars);

        assertThat(aggregated1H).hasSize(1);
        assertBar(aggregated1H.get(0),
                  "2018-06-05T05:00:00Z",
                  "2018-06-05T05:59:59.999Z",
                  "EURUSD",
                  H1,
                  116568,
                  116939,
                  116500,
                  116935
        );
    }

    @Test
    public void shouldFailOnBarsInWrongOrder() {
        final List<Bar> m5Bars = createBarListDescendingOf(3);
        Collections.reverse(m5Bars);
        assertThrownMessage(m5Bars, "BarAggregator requires bars sorted in descending time");
    }

    @Test
    public void shouldFailOnBarsMixedSymbols() {
        final List<Bar> m5Bars = List.of(createBar(REALTIME_UUID, "EURUSD", M5, 1528174800000L),
                                         createBar(REALTIME_UUID, "AUDUSD", M5, 1528174800000L));
        assertThrownMessage(m5Bars,
                            "BarAggregator does not support bars with different symbols.  First was EURUSD");
    }

    @Test
    public void shouldAggregateZeroBarsOk() {
        List<Bar> aggregated1H = new SmallToLargeBarAggregator(VALIDATOR).aggregate(H1, Collections.emptyList());

        assertThat(aggregated1H).hasSize(0);
    }

    @Test
    public void shouldAggregateTwelveBarsOk() {
        final List<Bar> m5Bars = createBarListDescendingOf(12);

        List<Bar> aggregated1H = new SmallToLargeBarAggregator(VALIDATOR).aggregate(H1, m5Bars);

        assertThat(aggregated1H).hasSize(1);
        assertBar(aggregated1H.get(0),
                  "2018-06-05T05:00:00Z",
                  "2018-06-05T05:59:59.999Z",
                  "EURUSD",
                  H1,
                  116568,
                  116939,
                  116500,
                  116935
        );
    }

    @Test
    public void shouldAggregateBarsAcrossPeriodBoundaries() {
        final List<Bar> m5Bars = createBarListDescendingOf(24);

        List<Bar> aggregated1H = new SmallToLargeBarAggregator(VALIDATOR).aggregate(H1, m5Bars);

        assertThat(aggregated1H).hasSize(2);
        assertBar(aggregated1H.get(0),
                  "2018-06-05T06:00:00Z",
                  "2018-06-05T06:59:59.999Z",
                  "EURUSD",
                  H1,
                  116568,
                  116939,
                  116500,
                  116935
        );
        assertBar(aggregated1H.get(1),
                  "2018-06-05T05:00:00Z",
                  "2018-06-05T05:59:59.999Z",
                  "EURUSD",
                  H1,
                  116568,
                  116939,
                  116500,
                  116935
        );
    }

    @Test
    public void shouldAggregateToOddNumberOfPartialBars() {
        final List<Bar> m5Bars = createBarListDescendingOf(18);

        List<Bar> aggregated1H = new SmallToLargeBarAggregator(VALIDATOR).aggregate(H1, m5Bars);

        assertThat(aggregated1H).hasSize(2);
        // oldest bar first, so we fill the past bar to capacity before the latest one.
        assertBar(aggregated1H.get(0),
                  "2018-06-05T06:00:00Z",
                  "2018-06-05T06:59:59.999Z",
                  "EURUSD",
                  H1,
                  116568,
                  116939,
                  116500,
                  116935
        );
        assertBar(aggregated1H.get(1),
                  "2018-06-05T05:00:00Z",
                  "2018-06-05T05:59:59.999Z",
                  "EURUSD",
                  H1,
                  116568,
                  116939,
                  116500,
                  116935
        );
    }

    @Test
    public void shouldNotAggregateLargeToSmall() {
        final List<Bar> m5Bars = List.of(createBar(REALTIME_UUID, "EURUSD", D1, 1528174800000L));
        assertThrownMessage(m5Bars,
                            "BarAggregator does not support bars with larger periods D1 that the target H1.");

    }

    @Test
    public void shouldNotAggregateMixedPeriods() {
        final List<Bar> m5Bars = List.of(createBar(REALTIME_UUID, "EURUSD", M10, 1528174800000L),
                                         createBar(REALTIME_UUID, "EURUSD", M5, 1528174800000L));
        assertThrownMessage(m5Bars,
                            "BarAggregator does not support bars with mixed periods.  First was M10.");

    }

    private static void assertThrownMessage(List<Bar> m5Bars, String message) {
        assertThatThrownBy(() -> new SmallToLargeBarAggregator(VALIDATOR)
                .aggregate(Bar.Period.H1, m5Bars)).isInstanceOf(IllegalArgumentException.class)
                                                  .hasMessage(message);
    }

    private static List<Bar> createBarListDescendingOf(int numBars) {
        return ModelPrototype.createBarListDescending(REALTIME_UUID,
                                                      "EURUSD",
                                                      M5,
                                                      1528174800000L,
                                                      numBars);
    }
}
