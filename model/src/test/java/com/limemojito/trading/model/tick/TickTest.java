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

package com.limemojito.trading.model.tick;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.limemojito.json.JsonLoader;
import com.limemojito.json.ObjectMapperPrototype;
import com.limemojito.test.JsonAsserter;
import com.limemojito.trading.model.UtcTimeUtils;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.limemojito.trading.model.ModelPrototype.createTick;
import static com.limemojito.trading.model.StreamData.REALTIME_UUID;
import static com.limemojito.trading.model.StreamData.StreamSource.Historical;
import static com.limemojito.trading.model.StreamData.StreamSource.Live;
import static com.limemojito.trading.model.StreamData.StreamType.Backtest;
import static com.limemojito.trading.model.StreamData.StreamType.Realtime;
import static org.assertj.core.api.Assertions.assertThat;

public class TickTest {

    private static final String EURUSD = "EURUSD";

    @Test
    public void tickShouldBeJsonable() {
        final Tick tick = createTick(EURUSD, 1528174843000L, 116928, Historical);
        assertThat(tick.getDateTimeUtc()).isEqualTo(UtcTimeUtils.toLocalDateTimeUtc(tick.getMillisecondsUtc()));
        assertThat(tick.getInstant()).isEqualTo(UtcTimeUtils.toInstant(tick.getMillisecondsUtc()));
        JsonAsserter.assertSerializeDeserialize(tick);
    }

    @Test
    public void shouldBeStableJson() {
        ObjectMapper objectMapper = ObjectMapperPrototype.buildBootLikeMapper();
        JsonLoader loader = new JsonLoader(objectMapper);

        final Tick tick = createTick(EURUSD, 1528174843000L, 116928, Historical);
        Tick version1 = loader.loadFrom("/model/tick-1.0.json", Tick.class);
        assertThat(tick).usingRecursiveComparison().isEqualTo(version1);
    }

    @Test
    public void shouldBeStreamAware() {
        final UUID backtestId = UUID.randomUUID();
        Tick one = createTick(REALTIME_UUID, EURUSD, 20000, 67000, Live);
        Tick two = createTick(backtestId, one.getSymbol(), one.getMillisecondsUtc(), one.getBid(), one.getSource());
        Tick three = createTick(REALTIME_UUID, EURUSD, one.getMillisecondsUtc() + 1, 67000, Live);
        Tick four = createTick(REALTIME_UUID, "USDCAD", one.getMillisecondsUtc(), one.getBid(), one.getSource());

        assertThat(one.getPartitionKey()).isEqualTo("00000000-0000-0000-0000-000000000000-EURUSD");

        assertThat(one.getStreamType()).isEqualTo(Realtime);
        assertThat(two.getStreamType()).isEqualTo(Backtest);

        assertThat(one.isInSameStream(two)).isFalse();
        //noinspection EqualsWithItself
        assertThat(one.equals(one)).isTrue();
        //noinspection EqualsWithItself
        assertThat(one.compareTo(one)).isEqualTo(0);
        assertThat(one.equals(two)).isFalse();
        assertThat(one.equals(three)).isFalse();
        assertThat(one.equals(four)).isFalse();

        // live is > backtest
        assertThat(one.compareTo(two)).isGreaterThan(0);
        assertThat(two.compareTo(one)).isLessThan(0);

        // time
        assertThat(one.compareTo(three)).isLessThan(0);
        assertThat(three.compareTo(one)).isGreaterThan(0);

        // symbol alphabetic
        assertThat(one.compareTo(four)).isLessThan(0);
        assertThat(four.compareTo(one)).isGreaterThan(0);
    }
}
