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

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DukascopyPathGeneratorTest {

    private final DukascopyPathGenerator generator = new DukascopyPathGenerator();

    @Test
    public void shouldGeneratePathsForDayRange() {
        List<String> paths = generatePathsFor("2018-01-01T00:00:00Z", "2018-01-01T23:59:59Z");

        assertThat(paths).hasSize(24);
        assertThat(paths.get(0)).isEqualTo("EURUSD/2018/00/01/00h_ticks.bi5");
        assertThat(paths.get(23)).isEqualTo("EURUSD/2018/00/01/23h_ticks.bi5");
    }

    @Test
    public void shouldGeneratePathsForTwoDayRange() {
        List<String> paths = generatePathsFor("2018-01-01T00:00:00Z", "2018-01-02T23:59:59Z");

        assertThat(paths).hasSize(2 * 24);
        assertThat(paths.get(0)).isEqualTo("EURUSD/2018/00/01/00h_ticks.bi5");
        assertThat(paths.get(47)).isEqualTo("EURUSD/2018/00/02/23h_ticks.bi5");
    }

    @Test
    public void shouldGeneratePathsForYearRange() {
        List<String> paths = generatePathsFor("2018-01-01T00:00:00Z", "2018-12-31T23:59:59Z");

        assertThat(paths).hasSize(365 * 24);
        assertThat(paths.get(0)).isEqualTo("EURUSD/2018/00/01/00h_ticks.bi5");
        assertThat(paths.get((365 * 24) - 1)).isEqualTo("EURUSD/2018/11/31/23h_ticks.bi5");
    }

    @Test
    public void shouldGeneratePathsForMultiYearRange() {
        List<String> paths = generatePathsFor("2018-01-01T00:00:00Z", "2021-12-31T23:59:59Z");

        int numHoursIncludingLeapYear = 35064;
        assertThat(paths).hasSize(numHoursIncludingLeapYear);
        assertThat(paths.get(0)).isEqualTo("EURUSD/2018/00/01/00h_ticks.bi5");
        assertThat(paths.get(numHoursIncludingLeapYear - 1)).isEqualTo("EURUSD/2021/11/31/23h_ticks.bi5");
    }


    @Test
    public void shouldGeneratePathsGroupedByDay() {
        List<List<String>> pathByDay = generator.generatePathsGroupedByDay("USDJPY",
                                                                           Instant.parse("2018-01-01T00:00:00Z"),
                                                                           Instant.parse("2018-12-31T23:59:59Z"));
        assertThat(pathByDay).hasSize(365);
        for (List<String> paths : pathByDay) {
            assertThat(paths).hasSize(24);
        }
        assertThat(pathByDay.get(0).get(0)).isEqualTo("USDJPY/2018/00/01/00h_ticks.bi5");
        assertThat(pathByDay.get(364).get(23)).isEqualTo("USDJPY/2018/11/31/23h_ticks.bi5");

    }

    @Test
    public void shouldRoundToHourPaths() {
        List<String> paths = generatePathsFor("2018-01-01T00:00:00Z", "2018-01-01T00:00:01Z");
        assertThat(paths).hasSize(1);
        assertThat(paths.get(0)).isEqualTo("EURUSD/2018/00/01/00h_ticks.bi5");
    }

    @Test
    public void shouldRoundToHourPathAcrossDays() {
        List<String> paths = generatePathsFor("2018-01-01T00:00:00Z", "2018-01-02T00:59:59Z");
        assertThat(paths).hasSize(25);
        assertThat(paths.get(0)).isEqualTo("EURUSD/2018/00/01/00h_ticks.bi5");
        assertThat(paths.get(24)).isEqualTo("EURUSD/2018/00/02/00h_ticks.bi5");
    }

    @Test
    public void shouldFailIfDatesAreNotARange() {
        assertThatThrownBy(() -> generatePathsFor("2018-01-01T00:00:00Z", "2018-01-01T00:00:00Z"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generatePathsFor("2018-01-03T00:00:00Z", "2018-01-02T00:00:00Z"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private List<String> generatePathsFor(String start, String end) {
        return generator.generatePaths("EURUSD", Instant.parse(start), Instant.parse(end));
    }
}
