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

package com.limemojito.trading.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static com.limemojito.trading.model.MarketStatus.Status.CLOSED;
import static com.limemojito.trading.model.MarketStatus.Status.OPEN;
import static org.assertj.core.api.Assertions.assertThat;

public class MarketStatusTest {

    private final MarketStatus marketStatus = new MarketStatus();

    @Test
    public void shouldFailPreSydneyStartAESTOnMonday() {
        assertState("2019-06-03T08:00:00+10:00", CLOSED);
    }

    @Test
    public void shouldFailPostNewYorkCloseOnFriday() {
        assertState("2019-06-07T17:01:00-04:00", CLOSED);
    }

    @Test
    public void shouldOpenForPastSydneyStart() {
        assertState("2019-06-03T09:00:00+10:00", OPEN);
    }

    @Test
    public void shouldOpenForPreNYClose() {
        assertState("2019-06-07T16:59:59-04:00", OPEN);
    }

    @Test
    public void shouldBeOpenForSydneyStartAEDT() {
        assertState("2019-02-04T09:00:00+11:00", OPEN);
    }

    @Test
    public void shouldBeClosedForPreSydneyStartAEDT() {
        assertState("2019-02-04T08:59:59+11:00", CLOSED);
    }

    @Test
    public void shouldBeClosedForPostNYWinterTime() {
        assertState("2019-11-08T16:59:59-05:00", OPEN);
        assertState("2019-11-08T17:00:00-05:00", CLOSED);
    }

    @Test
    public void shouldBeClosedForTestPullingZeroSize() {
        assertState("2019-07-07T12:00:00Z", CLOSED);
    }

    @Test
    public void shouldBeClosedForSaturdayInSydneyBy4pminNY() {
        assertState("2019-07-06T07:00:00+10:00", CLOSED);
    }

    @Test
    public void shouldBeClosedForJustBeforeOpenSydneyByNY() {
        assertState("2019-07-07T17:59:59-05:00", CLOSED);
    }

    private void assertState(String time, MarketStatus.Status expectedState) {
        assertThat(marketStatus.isOpen(OffsetDateTime.parse(time).toInstant()))
                .isEqualTo(expectedState);
    }
}
