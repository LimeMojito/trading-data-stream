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

package com.limemojito.trading.model.bar;

import com.limemojito.trading.model.TradingInputStream;
import com.limemojito.trading.model.stream.TradingCsvStream;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.List;

/**
 * Converts bars to format:
 * <code>
 * Epoch Time (UTC),Symbol,Period,Open,High,Low,Close
 * 2018-07-05 05:00:00,EURUSD,M10,116568,116571,116518,116519
 * </code>
 */
@Slf4j
public class BarInputStreamToCsv extends TradingCsvStream<Bar> {

    /**
     * Create a CSV writer over a bar input stream, writing to an {@link OutputStream}.
     *
     * @param barInputStream source of bars to read
     * @param outputStream   destination to write CSV bytes to
     * @throws IOException if the underlying stream cannot be written
     */
    public BarInputStreamToCsv(TradingInputStream<Bar> barInputStream, OutputStream outputStream) throws IOException {
        super(barInputStream, outputStream);
    }

    /**
     * Create a CSV writer over a bar input stream, writing to a character {@link Writer}.
     *
     * @param barInputStream source of bars to read
     * @param outputWriter   destination to write CSV text to
     * @throws IOException if the underlying writer cannot be written
     */
    public BarInputStreamToCsv(TradingInputStream<Bar> barInputStream, Writer outputWriter) throws IOException {
        super(barInputStream, outputWriter);
    }

    @Override
    protected List<String> getHeader() {
        return List.of("Epoch Time (UTC)", "Symbol", "Period", "Open", "High", "Low", "Close");
    }

    @Override
    protected List<Object> modelToFields(Bar bar) {
        return List.of(bar.getStartDateTimeUtc(),
                       bar.getSymbol(),
                       bar.getPeriod(),
                       bar.getOpen(),
                       bar.getHigh(),
                       bar.getLow(),
                       bar.getClose());
    }
}
