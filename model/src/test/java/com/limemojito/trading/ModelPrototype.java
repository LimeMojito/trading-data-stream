/*
 * Copyright 2011-2022 Lime Mojito Pty Ltd
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

package com.limemojito.trading;

import com.limemojito.trading.StreamData.StreamSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.limemojito.trading.StreamData.REALTIME_UUID;
import static com.limemojito.trading.StreamData.StreamSource.Historical;

public class ModelPrototype {
    static Bar createBar(UUID uuid, String symbol, Bar.Period period, long startMillisecondsUtc) {
        return Bar.builder()
                  .startMillisecondsUtc(startMillisecondsUtc)
                  .streamId(uuid)
                  .period(period)
                  .symbol(symbol)
                  .low(116500)
                  .high(116939)
                  .open(116568)
                  .close(116935)
                  .source(Historical)
                  .build();
    }

    static Tick createTick(String symbol, long startMillisecondsUtc, int bid, StreamSource streamSource) {
        return createTick(REALTIME_UUID, symbol, startMillisecondsUtc, bid, streamSource);
    }

    static Tick createTick(UUID streamId, String symbol, long startMillisecondsUtc, int bid, StreamSource streamSource) {
        return Tick.builder()
                   .streamId(streamId)
                   .millisecondsUtc(startMillisecondsUtc)
                   .symbol(symbol)
                   .ask(116939)
                   .bid(bid)
                   .askVolume(3.45f)
                   .bidVolume(1.28f)
                   .source(streamSource)
                   .build();
    }

    public static List<Bar> createBarListDescending(UUID stream, String symbol, Bar.Period period, long startTimeUtc, int numBars) {
        List<Bar> bars = new ArrayList<>();
        for (int i = numBars - 1; i >= 0; i--) {
            Bar bar = createBar(stream, symbol, period, startTimeUtc + (i * period.getDurationMilliseconds()));
            bars.add(bar);
        }
        return bars;
    }
}
