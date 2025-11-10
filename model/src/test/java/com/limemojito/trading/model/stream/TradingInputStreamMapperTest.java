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

package com.limemojito.trading.model.stream;

import com.limemojito.trading.model.TradingInputStream;
import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.bar.BarListInputStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static com.limemojito.trading.model.ModelPrototype.createBarListDescending;
import static com.limemojito.trading.model.StreamData.REALTIME_UUID;
import static com.limemojito.trading.model.bar.Bar.Period.H1;
import static com.limemojito.trading.model.bar.BarVisitor.NO_VISITOR;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("resource")
@Slf4j
public class TradingInputStreamMapperTest {

    private final String eurusd = "EURUSD";
    private final Instant instant = Instant.parse("2019-05-18T14:00:00Z");

    @Test
    public void shouldCover() {
        new TradingInputStreamMapper() {
        };
    }

    @Test
    public void shouldCreateBarListUsingCollectionStream() {
        List<Bar> bars = createBarListDescending(REALTIME_UUID,
                                                 eurusd,
                                                 H1,
                                                 instant.toEpochMilli(),
                                                 5);
        BarListInputStream barListInputStream = new BarListInputStream(bars, NO_VISITOR);

        assertThat(barListInputStream.stream().collect(toList())).containsAll(bars);
    }

    @Test
    public void shouldFireOnCloseForCollectionStream() throws Exception {
        List<Bar> bars = createBarListDescending(REALTIME_UUID,
                                                 eurusd,
                                                 H1,
                                                 instant.toEpochMilli(),
                                                 5);

        try (TradingInputStream<Bar> onClose = TradingInputStreamMapper.streamFrom(bars, NO_VISITOR, () -> log.info("OnClose"))) {
            assertThat(onClose.stream().collect(toList())).containsAll(bars);
        }
    }

    @Test
    public void shouldStreamFromNoDecorators() throws Exception {
        List<Bar> bars = createBarListDescending(REALTIME_UUID,
                                                 eurusd,
                                                 H1,
                                                 instant.toEpochMilli(),
                                                 5);

        try (TradingInputStream<Bar> onClose = TradingInputStreamMapper.streamFrom(bars)) {
            assertThat(onClose.stream().collect(toList())).containsAll(bars);
        }
    }

    @Test
    public void shouldTransformOk() throws IOException {
        TradingInputStream<Bar> bars = new BarListInputStream(createBarListDescending(REALTIME_UUID,
                                                                                      eurusd,
                                                                                      H1,
                                                                                      instant.toEpochMilli(),
                                                                                      50),
                                                              NO_VISITOR);
        try (TradingInputStream<Instant> map = TradingInputStreamMapper.map(bars, Bar::getStartInstant)) {
            assertThat(map.stream().collect(toList())).containsAll(bars.stream().map(Bar::getStartInstant).collect(toList()));
        }
    }

    @Test
    public void shouldSuppressExceptionInOnCloseWhenMapping() throws IOException {
        TradingInputStream<Bar> bars = new BarListInputStream(createBarListDescending(REALTIME_UUID,
                                                                                      eurusd,
                                                                                      H1,
                                                                                      instant.toEpochMilli(),
                                                                                      2),
                                                              NO_VISITOR);
        try (TradingInputStream<Instant> map = TradingInputStreamMapper.map(bars, Bar::getStartInstant, () -> {
            throw new RuntimeException("bang");
        })) {
            assertThat(map.stream().collect(toList())).containsAll(bars.stream().map(Bar::getStartInstant).collect(toList()));
        }
    }
}
