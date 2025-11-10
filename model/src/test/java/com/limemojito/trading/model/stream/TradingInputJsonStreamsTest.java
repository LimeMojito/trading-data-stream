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

package com.limemojito.trading.model.stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limemojito.json.ObjectMapperPrototype;
import com.limemojito.test.JsonAsserter;
import com.limemojito.trading.model.ModelPrototype;
import com.limemojito.trading.model.TradingInputStream;
import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.bar.BarListInputStream;
import com.limemojito.trading.model.bar.BarVisitor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.limemojito.trading.model.StreamData.REALTIME_UUID;
import static com.limemojito.trading.model.StreamData.StreamSource.Historical;
import static com.limemojito.trading.model.bar.Bar.Period.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TradingInputJsonStreamsTest {

    private final ObjectMapper mapper = ObjectMapperPrototype.buildBootLikeMapper();
    private final TradingInputJsonStreams jsonStreams = new TradingInputJsonStreams(mapper);
    private final TypeReference<List<Bar>> barListType = new TypeReference<>() {
    };

    @Test
    public void modelsShouldBeJsonable() throws Exception {
        JsonAsserter.assertSerializeDeserialize(ModelPrototype.createTick("EURUSD", 84365834L, 24, Historical));
        JsonAsserter.assertSerializeDeserialize(ModelPrototype.createBar(UUID.randomUUID(), "EURUSD", H1, 84365834L));
    }

    @Test
    public void shouldReadBarJsonArray() throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream("/EURUSD/2021/11/01/00h_M5_bars.json");
             TradingInputStream<Bar> barStream = jsonStreams.createStream(inputStream, Bar.class)) {
            List<Bar> bars = barStream.stream().collect(Collectors.toList());
            assertThat(bars).hasSize(12);
            assertThat(bars.get(8).getStartInstant()).isEqualTo("2021-12-01T00:40:00Z");
            assertThat(bars.get(8).getOpen()).isEqualTo(113321);
        }
    }

    @Test
    public void shouldWriteToJsonArray() throws Exception {
        List<Bar> bars = ModelPrototype.createBarListDescending(REALTIME_UUID, "CHFUSD", M15, 1638319200000L, 34);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            jsonStreams.writeAsJsonArray(new BarListInputStream(bars, BarVisitor.NO_VISITOR), outputStream);
            List<Bar> reconstituted = mapper.readValue(outputStream.toByteArray(), barListType);
            assertThat(reconstituted).isEqualTo(bars);
        }
    }

    @Test
    public void shouldWriteCollectionToJsonArray() throws Exception {
        List<Bar> bars = ModelPrototype.createBarListDescending(REALTIME_UUID, "AUDUSD", H4, 1638319200000L, 34);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            jsonStreams.writeAsJsonArray(bars, outputStream);
            List<Bar> reconstituted = mapper.readValue(outputStream.toByteArray(), barListType);
            assertThat(reconstituted).isEqualTo(bars);
        }
    }

    @Test
    public void shouldApplyVisitorToReconstitutedArray() throws Exception {
        AtomicInteger count = new AtomicInteger();
        try (InputStream inputStream = getClass().getResourceAsStream("/EURUSD/2021/11/01/00h_M5_bars.json");
             TradingInputStream<Bar> barStream = jsonStreams.createStream(inputStream,
                                                                          Bar.class,
                                                                          bar -> count.incrementAndGet())) {
            assertThat(barStream.stream().collect(Collectors.toList())).hasSize(12);
        }
        assertThat(count.get()).isEqualTo(12);
    }

    @Test
    public void shouldFailWhenNotReadingJson() throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream("/json/empty.json");
             TradingInputStream<Bar> barStream = jsonStreams.createStream(inputStream, Bar.class)) {
            assertThat(barStream.hasNext()).isFalse();
            assertThatThrownBy(barStream::next).isInstanceOf(NoSuchElementException.class);
        }
    }

}
