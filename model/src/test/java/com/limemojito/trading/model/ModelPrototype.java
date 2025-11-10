/*
 * Copyright 2011-2024 Lime Mojito Pty Ltd
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limemojito.json.JsonLoader;
import com.limemojito.trading.model.StreamData.StreamSource;
import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.tick.Tick;
import com.limemojito.trading.model.tick.dukascopy.DukascopyUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.limemojito.trading.model.StreamData.REALTIME_UUID;
import static com.limemojito.trading.model.StreamData.StreamSource.Historical;
import static org.assertj.core.api.Assertions.assertThat;

public class ModelPrototype {

    private static final ObjectMapper OBJECT_MAPPER = DukascopyUtils.setupObjectMapper();
    private static final JsonLoader JSON_LOADER = new JsonLoader(OBJECT_MAPPER);

    public static String toJson(List<Bar> bars) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(bars);
    }

    public static List<Bar> loadBars(String resourcePath) throws IOException {
        return JSON_LOADER.loadFrom(resourcePath, new TypeReference<>() {
        });
    }

    public static InputStream loadStream(String resourcePath) {
        InputStream resourceAsStream = ModelPrototype.class.getResourceAsStream(resourcePath);
        assertThat(resourceAsStream).withFailMessage("Could not load %s", resourcePath)
                                    .isNotNull();
        return resourceAsStream;
    }

    public static Bar createBar(UUID uuid, String symbol, Bar.Period period, long startMillisecondsUtc) {
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

    public static Tick createTick(String symbol, long startMillisecondsUtc, int bid, StreamSource streamSource) {
        return createTick(REALTIME_UUID, symbol, startMillisecondsUtc, bid, streamSource);
    }

    public static Tick createTick(UUID streamId,
                                  String symbol,
                                  long startMillisecondsUtc,
                                  int bid,
                                  StreamSource streamSource) {
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

    public static List<Bar> createBarListDescending(UUID stream,
                                                    String symbol,
                                                    Bar.Period period,
                                                    long startTimeUtc,
                                                    int numBars) {
        List<Bar> bars = new ArrayList<>();
        for (int i = numBars - 1; i >= 0; i--) {
            Bar bar = createBar(stream, symbol, period, startTimeUtc + (i * period.getDurationMilliseconds()));
            bars.add(bar);
        }
        return bars;
    }
}
