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

package com.limemojito.trading.model.bar;

import com.limemojito.trading.model.TradingInputStream;
import com.limemojito.trading.model.stream.TradingInputStreamMapper;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Adapts an in memory list of bars to a trading input stream.
 *
 * @see TradingInputStreamMapper for generic versions.
 */
public class BarListInputStream implements TradingInputStream<Bar> {
    private final TradingInputStream<Bar> delegate;

    /**
     * Create an input stream over a fixed list of bars, optionally visiting each bar as it is emitted.
     *
     * @param barList    bars to stream in their current order
     * @param barVisitor optional visitor invoked for each bar when read
     */
    public BarListInputStream(List<Bar> barList, BarVisitor barVisitor) {
        this.delegate = TradingInputStreamMapper.streamFrom(barList, barVisitor);
    }

    /**
     * Return the next bar from the underlying list.
     *
     * @throws NoSuchElementException if no more bars are available
     */
    @Override
    public Bar next() throws NoSuchElementException {
        return delegate.next();
    }

    /**
     * Whether another bar is available.
     */
    @Override
    public boolean hasNext() {
        return delegate.hasNext();
    }

    /**
     * Close the underlying resources, if any.
     */
    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
