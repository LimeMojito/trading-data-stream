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

package com.limemojito.trading.model.tick;

import com.limemojito.trading.model.TradingInputStream;
import com.limemojito.trading.model.stream.TradingCsvStream;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.List;

/**
 * Converts tick stream to format:
 * <code>
 * Epoch Time (UTC),Ask,Ask Volume,Bid,Bid Volume
 * 2018-07-05 05:00:01.08,116573,1.76,116568,4.76
 * </code>
 */
@Slf4j
public class TickInputStreamToCsv extends TradingCsvStream<Tick> {
    public TickInputStreamToCsv(TradingInputStream<Tick> tickInputStream,
                                OutputStream outputStream) throws IOException {
        super(tickInputStream, outputStream);
    }

    public TickInputStreamToCsv(TradingInputStream<Tick> tickInputStream, Writer writer) throws IOException {
        super(tickInputStream, writer);
    }

    @Override
    protected List<String> getHeader() {
        return List.of("Epoch Time (UTC)", "Ask", "Ask Volume", "Bid", "Bid Volume");
    }

    @Override
    protected List<Object> modelToFields(Tick tick) {
        return List.of(tick.getDateTimeUtc(), tick.getAsk(), tick.getAskVolume(), tick.getBid(), tick.getBidVolume());
    }
}
