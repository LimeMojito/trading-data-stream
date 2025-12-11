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

import com.limemojito.trading.model.TradingInputStream;
import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.tick.Tick;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.ThrowableAssert;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.limemojito.trading.model.bar.Bar.Period.*;
import static java.time.DayOfWeek.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
public class DukascopySearchTest {

    private final DukascopySearch search = DukascopyUtils.standaloneSetup();

    @Test
    public void shouldFailIfStartBeforeBeginningOfTime() {
        Instant start = Instant.parse("2009-01-02T00:59:59Z");
        Instant end = Instant.parse("2024-01-02T00:00:00Z");
        String expectedMessage = "Start 2009-01-02T00:59:59Z must be after 2020-01-01T00:00:00Z";
        assertArgumentFailure(expectedMessage,
                              () -> search.search("EURUSD",
                                                  start,
                                                  end));
    }

    @Test
    @SuppressWarnings("resource")
    public void shouldFailIfAfterBeforeStart() {
        Instant start = Instant.parse("2024-01-02T00:59:59Z");
        Instant end = Instant.parse("2021-01-02T00:00:00Z");
        String expectedMessage = "Instant 2024-01-02T00:59:59Z must be before 2021-01-02T00:00:00Z";
        assertArgumentFailure(expectedMessage,
                              () -> search.search("EURUSD",
                                                  start,
                                                  end));
    }

    @Test
    @SuppressWarnings("resource")
    public void shouldFailIfEndPastTheBeginningOfTime() {
        assertThat(search.getTheBeginningOfTime()).isEqualTo("2020-01-01T00:00:00Z");
        search.setTheBeginningOfTime(Instant.parse("2018-01-01T00:00:00Z"));
        assertThat(search.getTheBeginningOfTime()).isEqualTo("2018-01-01T00:00:00Z");
        Instant start = Instant.parse("2009-01-02T00:59:59Z");
        Instant end = Instant.parse("2020-01-02T00:00:00Z");
        String expectedMessage = "Start 2009-01-02T00:59:59Z must be after 2018-01-01T00:00:00Z";
        assertArgumentFailure(expectedMessage,
                              () -> search.search("EURUSD",
                                                  start,
                                                  end));
        assertArgumentFailure(expectedMessage,
                              () -> search.search("AUDUSD",
                                                  start,
                                                  end,
                                                  tick -> {
                                                  }));
        assertArgumentFailure(expectedMessage,
                              () -> search.aggregateFromTicks("USDJPY",
                                                              H1,
                                                              start,
                                                              end));
        assertArgumentFailure(expectedMessage,
                              () -> search.aggregateFromTicks("AUDUSD",
                                                              H1,
                                                              start,
                                                              end,
                                                              bar -> {
                                                              }));
    }

    @Test
    public void shouldAggregateAcrossNoDataSpans() throws Exception {
        final Set<Bar> last = new HashSet<>();
        try (TradingInputStream<Bar> eurusd = search.aggregateFromTicks("EURUSD",
                                                                        H1,
                                                                        Instant.parse("2018-12-31T00:00:00Z"),
                                                                        Instant.parse("2019-01-01T10:00:00Z"))) {
            AtomicInteger count = new AtomicInteger();
            eurusd.forEach(bar -> {
                log.info("Found bar @ {}", bar.getStartInstant());
                count.incrementAndGet();
                if (last.iterator().hasNext()) {
                    assertThat(bar.getStartInstant()).isAfter(last.iterator().next().getStartInstant());
                    last.clear();
                }
                last.add(bar);
            });
            assertThat(count.get()).isEqualTo(23);
        }
    }

    @Test
    public void shouldBarCountForwards() throws Exception {
        int expectedBarCount = 10;
        List<Bar> bars;
        final Instant start = createInstantConfirming("2020-01-04T18:00:00Z", SATURDAY);
        try (TradingInputStream<Bar> stream = search.aggregateFromTicks("EURUSD",
                                                                        H1,
                                                                        start,
                                                                        expectedBarCount)) {
            bars = stream.stream().collect(Collectors.toList());
        }
        assertThat(bars).hasSize(expectedBarCount);
        // this has run over a weekend gap
        final Instant startInstant = bars.getFirst().getStartInstant();
        verifyInstantIsOnUTC(startInstant, SUNDAY);
        assertThat(startInstant).isEqualTo("2020-01-05T22:00:00Z");
        verifyInstantIsEqualTo(startInstant, "2020-01-06T09:00:00+11:00");
        assertThat(bars.get(expectedBarCount - 1).getStartInstant()).isEqualTo("2020-01-06T07:00:00Z");
    }

    private void verifyInstantIsEqualTo(Instant startInstant, String zonedDateTime) {
        assertThat(startInstant).isEqualTo(ZonedDateTime.parse(zonedDateTime).toInstant());
    }

    @Test
    public void shouldBarCountBackwardsThroughAWeekend() throws Exception {
        int expectedBarCount = 10;
        final Instant start = createInstantConfirming("2020-06-15T01:59:59Z", MONDAY);
        try (TradingInputStream<Bar> stream = search.aggregateFromTicks("EURUSD",
                                                                        H1,
                                                                        expectedBarCount,
                                                                        start)) {
            List<Bar> data = stream.stream().toList();
            data.forEach(bar -> log.info("Found bar @ {}", bar.getStartInstant()));
            assertThat(data.size()).isEqualTo(expectedBarCount);
            assertThat(data.getFirst().getStartInstant()).isEqualTo("2020-06-12T14:00:00Z");
            assertThat(data.get(expectedBarCount - 1).getStartInstant()).isEqualTo("2020-06-15T01:00:00Z");
        }
    }

    @Test
    public void shouldNotHaveDuplicateBarsInReverse() throws Exception {
        try (TradingInputStream<Bar> bars = search.aggregateFromTicks("EURUSD",
                                                                      H1,
                                                                      80,
                                                                      Instant.parse("2024-07-07T16:00:00Z"))) {
            assertStreamOk(bars, 80);
        }
    }

    @Test
    public void shouldNotHaveDuplicateBarsInForward() throws Exception {
        try (TradingInputStream<Bar> bars = search.aggregateFromTicks("EURUSD",
                                                                      H1,
                                                                      Instant.parse("2024-07-07T16:00:00Z"),
                                                                      80)) {
            assertStreamOk(bars, 80);
        }
    }

    @Test
    public void shouldStopAtTheBeginningOfTime() throws Exception {
        int expectedBarCount = 55;
        List<Bar> bars;
        final Instant start = createInstantConfirming("2020-01-06T05:00:00Z", MONDAY);
        try (TradingInputStream<Bar> stream = search.aggregateFromTicks("EURUSD",
                                                                        H1,
                                                                        100,
                // end time is EXCLUSIVE
                                                                        start)) {
            bars = stream.stream().collect(Collectors.toList());
        }
        assertThat(bars).hasSize(expectedBarCount);
        // this has run over a weekend gap
        assertThat(bars.getFirst().getStartInstant()).isEqualTo("2020-01-01T22:00:00Z");
        assertThat(bars.get(expectedBarCount - 1).getStartInstant()).isEqualTo("2020-01-06T04:00:00Z");
    }

    @Test
    public void shouldCountBackwardsWithBarVisitor() throws Exception {
        int expectedBarCount = 5;
        try (TradingInputStream<Bar> stream = search.aggregateFromTicks("EURUSD",
                                                                        H1,
                                                                        expectedBarCount,
                                                                        Instant.parse("2020-04-08T18:00:00Z"),
                                                                        bar -> log.info("Visited {}", bar))) {
            assertStreamOk(stream, expectedBarCount);
        }
    }

    @Test
    public void shouldCountForwardsWithBarVisitor() throws Exception {
        int expectedBarCount = 5;
        try (TradingInputStream<Bar> stream = search.aggregateFromTicks("EURUSD",
                                                                        H1,
                                                                        Instant.parse("2020-04-08T13:00:00Z"),
                                                                        expectedBarCount,
                                                                        bar -> log.info("Visited {}", bar))) {
            assertStreamOk(stream, expectedBarCount);
        }
    }

    @Test
    public void shouldSearchForTicksAndFilter() throws Exception {
        searchTicksExpect("EURUSD", "2020-01-02T00:00:00Z", "2020-01-02T00:59:59Z", 1268);
        searchTicksExpect("USDJPY", "2020-01-02T00:00:00Z", "2020-01-02T00:59:59Z", 994);
        searchTicksExpect("EURUSD", "2020-01-02T00:00:00Z", "2020-01-02T00:29:59Z", 717);
    }

    @Test
    public void shouldHandleMultiStream() throws Exception {
        searchTicksExpect("EURUSD", "2020-01-03T00:00:00Z", "2020-01-04T00:59:59Z", 87468);
    }

    @Test
    public void shouldHandleExpansionToEndOfSeconds() throws Exception {
        int expectedIncludingEnd = 1268;
        searchTicksExpect("EURUSD", "2020-01-02T00:00:00Z", "2020-01-02T00:59:59Z", expectedIncludingEnd);
        searchTicksExpect("EURUSD", "2020-01-02T00:00:00Z", "2020-01-02T00:59:59.999Z", expectedIncludingEnd);

    }

    @Test
    public void shouldAggregateBars() throws Exception {
        searchBarsExpect("EURUSD", H1, "2024-01-02T00:00:00Z", "2024-01-02T00:59:59Z", 1);
        searchBarsExpect("USDCHF", M5, "2020-01-02T00:00:00Z", "2020-01-02T00:59:59Z", 12);
        searchBarsExpect("USDCHF", M10, "2020-01-02T00:00:00Z", "2020-01-02T00:59:59Z", 6);
        searchBarsExpect("USDCHF", M30, "2020-01-02T00:00:00Z", "2020-01-02T00:59:59Z", 2);
        searchBarsExpect("USDCHF", H1, "2020-01-02T00:00:00Z", "2020-01-02T00:59:59Z", 1);
        // note that this is a partial bar.
        searchBarsExpect("USDCHF", H4, "2020-01-02T00:00:00Z", "2020-01-02T00:59:59Z", 1);
    }

    @Test
    public void shouldWorkWithMain() throws Exception {
        DukascopySearch.main("EURUSD", "M5", "2020-01-02T00:00:00Z", "2020-01-02T00:59:59Z");
    }

    @Test
    public void shouldGetBarsWithVisitor() throws Exception {
        AtomicInteger barCounter = new AtomicInteger();
        try (TradingInputStream<Bar> stream = search.aggregateFromTicks("NZDUSD",
                                                                        M5,
                                                                        Instant.parse("2020-01-02T00:00:00Z"),
                                                                        Instant.parse("2020-01-02T00:59:59Z"),
                                                                        (bar) -> barCounter.incrementAndGet())) {
            for (Bar bar : stream) {
                log.info("Counting bar {} via visitor", bar);
            }
        }
        assertThat(barCounter.get()).isEqualTo(12);
    }

    private void searchBarsExpect(String symbol,
                                  Bar.Period period,
                                  String start, String end, int expected) throws IOException {
        try (TradingInputStream<Bar> stream = search.aggregateFromTicks(symbol,
                                                                        period,
                                                                        Instant.parse(start),
                                                                        Instant.parse(end))) {
            assertStreamOk(stream, expected);
        }
    }

    private void searchTicksExpect(String symbol, String start, String end, int expected) throws IOException {
        try (TradingInputStream<Tick> stream = search.search(symbol,
                                                             Instant.parse(start),
                                                             Instant.parse(end))) {
            assertStreamOk(stream, expected);
        }
    }

    private static <Model> void assertStreamOk(TradingInputStream<Model> stream, int expectedCount) {
        List<Model> models = stream.stream().collect(Collectors.toList());
        for (int i = 1; i < models.size(); i++) {
            // check for duplicates
            assertThat(models.get(i - 1)).isNotEqualTo(models.get(i));
        }
        assertThat(models).hasSize(expectedCount);
    }

    private static void assertArgumentFailure(String expectedMessage, ThrowableAssert.ThrowingCallable method) {
        assertThatThrownBy(method)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    private static @NonNull Instant createInstantConfirming(String instant, DayOfWeek dayOfWeek) {
        final Instant start = Instant.parse(instant);
        verifyInstantIsOnUTC(start, dayOfWeek);
        return start;
    }

    private static void verifyInstantIsOnUTC(Instant start, DayOfWeek dayOfWeek) {
        assertThat(LocalDateTime.ofInstant(start, ZoneId.of("UTC")).getDayOfWeek()).isEqualTo(dayOfWeek);
    }
}
