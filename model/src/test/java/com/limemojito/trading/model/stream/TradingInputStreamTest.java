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
import com.limemojito.trading.model.tick.Tick;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.limemojito.trading.model.TickDataLoader.createTickInputStreamFromClasspath;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class TradingInputStreamTest {
    private final int knownCount = 6578;
    private final int knownBidCount = 88;

    @Test
    public void shouldProduceStream() throws Exception {
        AtomicInteger tickCounter = new AtomicInteger();
        try (TradingInputStream<Tick> inputStream = createInputStream()) {
            Stream<Tick> stream = inputStream.stream();
            stream.forEach((tick) -> tickCounter.incrementAndGet());
        }

        assertThat(tickCounter.get()).isEqualTo(knownCount);
    }

    @Test
    public void shouldIterate() throws Exception {
        performTickCount(createInputStream(), knownCount);
    }

    @Test
    public void shouldCombineTwoStreams() throws Exception {
        List<TradingInputStream<Tick>> streams = List.of(createInputStream(), createInputStream());
        performTickCount(TradingInputStream.combine(streams), knownCount * 2);
    }

    @Test
    public void shouldCombineOneStreamAndFilter() throws Exception {
        List<TradingInputStream<Tick>> streams = List.of(createInputStream());
        Predicate<Tick> filter = (tick) -> tick.getBid() == 116938;
        performTickCount(TradingInputStream.combine(streams, filter), knownBidCount);
    }

    @Test
    public void shouldCombineTwoStreamAndFilter() throws Exception {
        List<TradingInputStream<Tick>> streams = List.of(createInputStream(), createInputStream());
        Predicate<Tick> filter = (tick) -> tick.getBid() == 116938;
        performTickCount(TradingInputStream.combine(streams, filter), knownBidCount * 2);
    }

    @Test
    public void shouldCombineThreeStreamAndFilter() throws Exception {
        List<TradingInputStream<Tick>> streams = List.of(createInputStream(), createEmptyStream(), createInputStream());
        Predicate<Tick> filter = (tick) -> tick.getBid() == 116938;
        performTickCount(TradingInputStream.combine(streams, filter), knownBidCount * 2);
    }

    private TradingInputStream<Tick> createEmptyStream() {
        return new TradingInputStream<>() {
            @Override
            public Tick next() throws NoSuchElementException {
                throw new NoSuchElementException("Empty stream");
            }

            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public void close() {

            }
        };
    }

    private static void performTickCount(TradingInputStream<Tick> stream, int expectedCount) throws IOException {
        try (TradingInputStream<Tick> inputStream = stream) {
            int tickCount = 0;
            for (Tick ignored : inputStream) {
                tickCount++;
            }
            assertThat(tickCount).isEqualTo(expectedCount);
        }
    }

    private TradingInputStream<Tick> createInputStream() {
        String path = "EURUSD/2018/06/05/06h_ticks.bi5";
        return createTickInputStreamFromClasspath(path);
    }
}
