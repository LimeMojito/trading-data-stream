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

import java.util.UUID;

import static com.limemojito.trading.model.StreamData.REALTIME_UUID;
import static com.limemojito.trading.model.StreamData.StreamType.Backtest;
import static com.limemojito.trading.model.StreamData.StreamType.Realtime;
import static org.assertj.core.api.Assertions.assertThat;

public class StreamDataTest {
    @Test
    public void shouldDifferentiateLiveAndBacktest() {
        assertThat(StreamData.isRealtime(REALTIME_UUID)).isTrue();
        assertThat(StreamData.isBacktest(REALTIME_UUID)).isFalse();
        assertThat(StreamData.isBacktest(UUID.randomUUID())).isTrue();
        assertThat(StreamData.isBacktest(REALTIME_UUID)).isFalse();
        assertThat(StreamData.type(REALTIME_UUID)).isEqualTo(Realtime);
        assertThat(StreamData.type(UUID.randomUUID())).isEqualTo(Backtest);
    }
}
