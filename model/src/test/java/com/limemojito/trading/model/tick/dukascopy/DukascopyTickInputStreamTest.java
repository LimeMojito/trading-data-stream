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

import com.limemojito.trading.model.StreamData;
import com.limemojito.trading.model.TickDataLoader;
import com.limemojito.trading.model.TradingInputStream;
import com.limemojito.trading.model.tick.Tick;
import com.limemojito.trading.model.tick.dukascopy.cache.DirectDukascopyNoCache;
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.limemojito.trading.model.StreamData.StreamSource.Historical;
import static com.limemojito.trading.model.StreamData.StreamType.Realtime;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DukascopyTickInputStreamTest {
    private static final String EURUSD = "EURUSD";
    private static final Validator VALIDATOR = DukascopyUtils.setupValidator();

    @Test
    public void shouldRunFromCache() throws Exception {
        final AtomicInteger visitCounter = new AtomicInteger();
        int tickCount = 0;
        DirectDukascopyNoCache cache = new DirectDukascopyNoCache();
        try (DukascopyTickInputStream input = new DukascopyTickInputStream(VALIDATOR,
                                                                           cache,
                                                                           "EURUSD/2018/06/05/05h_ticks.bi5",
                                                                           tick -> visitCounter.incrementAndGet())) {
            for (Tick ignored : input) {
                tickCount++;
            }
        }
        assertThat(visitCounter.get()).isEqualTo(tickCount);
        assertThat(cache.getRetrieveCount()).isEqualTo(1);
        assertThat(cache.cacheStats()).isEqualTo("DirectDukascopyNoCache: 1 retrieve(s) 0 retry(s)");
        assertThat(cache.getHitCount()).isEqualTo(0);
        assertThat(cache.getMissCount()).isEqualTo(1);
    }

    @Test
    public void shouldRunFromCacheNoVisitor() throws Exception {
        DirectDukascopyNoCache cache = new DirectDukascopyNoCache();
        try (DukascopyTickInputStream input = new DukascopyTickInputStream(VALIDATOR,
                                                                           cache,
                                                                           "EURUSD/2018/06/05/05h_ticks.bi5")) {
            input.next();
        }
        assertThat(cache.getRetrieveCount()).isEqualTo(1);
    }

    @Test
    public void shouldFailOnBrokenPath() throws Exception {
        byte[] bytes = lzmaCompress("I am not a tick".getBytes(UTF_8));

        assertThatThrownBy(() -> new DukascopyTickInputStream(VALIDATOR,
                                                              "EURUSD/2018/06/05h_ticks.bi5",
                                                              new ByteArrayInputStream(bytes)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Can not parse path /2018/06/05h_ticks.bi5");

    }

    @Test
    public void shouldGoBangOnInvalidTick() throws IOException {
        byte[] bytes = lzmaCompress("I am not a tick".getBytes(UTF_8));
        DukascopyTickInputStream input = new DukascopyTickInputStream(VALIDATOR,
                                                                      "EURUSD/2018/06/05/05h_ticks.bi5",
                                                                      new ByteArrayInputStream(bytes));

        assertThatThrownBy(input::next).isInstanceOf(IOException.class)
                                       .hasMessage("Corrupted data - read 15 expected 20");
    }

    @Test
    public void shouldStreamToEnd() throws Exception {
        final String path = "EURUSD/2018/06/05/05h_ticks.bi5";
        final AtomicInteger visitCounter = new AtomicInteger();
        try (TradingInputStream<Tick> input = new DukascopyTickInputStream(VALIDATOR,
                                                                           path,
                                                                           getClass().getResourceAsStream("/" + path),
                                                                           tick -> visitCounter.incrementAndGet())) {
            int tickCount = 0;
            for (Tick ignored : input) {
                tickCount++;
            }
            assertThat(visitCounter.get()).isEqualTo(tickCount);
            assertThatThrownBy(input::next).isInstanceOf(NoSuchElementException.class)
                                           .hasMessage("No more ticks from EURUSD/2018/06/05/05h_ticks.bi5");
        }
    }

    @Test
    public void shouldStreamData() throws Exception {
        final String path = "EURUSD/2018/06/05/05h_ticks.bi5";

        final List<Tick> tickList = TickDataLoader.loadTickData(path);

        final int expectedSize = 5594;
        assertThat(tickList).hasSize(expectedSize);
        assertTickEquals(tickList.get(0), 1530766801080L, 116573, 116568, 1.76F, 4.76F);
        assertTickEquals(tickList.get(expectedSize - 1), 1530770399812L, 116939, 116935, 1.25F, 1.0F);
    }

    @Test
    public void shouldStreamWithAnnoyingZeroBasedMonthPathAndZeroLengthFile() throws Exception {
        final String path = "USDJPY/2016/00/01/00h_ticks.bi5";

        final List<Tick> tickList = TickDataLoader.loadTickData(path);
        assertThat(tickList).isEmpty();
    }

    private void assertTickEquals(Tick tick, long time, int ask, int bid, float askVolume, float bidVolume) {
        assertThat(tick.getStreamId()).isEqualTo(StreamData.REALTIME_UUID);
        assertThat(tick.getStreamType()).isEqualTo(Realtime);
        assertThat(tick.getMillisecondsUtc()).isEqualTo(time);
        assertThat(tick.getSymbol()).isEqualTo(EURUSD);
        assertThat(tick.getAsk()).isEqualTo(ask);
        assertThat(tick.getBid()).isEqualTo(bid);
        assertThat(tick.getAskVolume()).isEqualTo(askVolume);
        assertThat(tick.getBidVolume()).isEqualTo(bidVolume);
        assertThat(tick.getSource()).isEqualTo(Historical);
    }

    private static byte[] lzmaCompress(byte[] data) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (LZMACompressorOutputStream lzmaCompressorOutputStream = new LZMACompressorOutputStream(outputStream)) {
            lzmaCompressorOutputStream.write(data);
            lzmaCompressorOutputStream.finish();
        }
        return outputStream.toByteArray();
    }

}
