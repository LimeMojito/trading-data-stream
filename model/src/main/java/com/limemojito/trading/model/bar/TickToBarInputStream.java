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
import com.limemojito.trading.model.tick.Tick;
import jakarta.validation.Validator;

import java.io.IOException;
import java.util.Iterator;

import static com.limemojito.trading.model.bar.BarVisitor.NO_VISITOR;

/**
 * Adapts a tick input stream to an input stream of bars by aggregating ticks in-memory
 * into the requested {@link Bar.Period}. Conversion is performed lazily on first access.
 *
 * @see TickToBarList
 */
public class TickToBarInputStream implements TradingInputStream<Bar> {
    private final TickToBarList delegate;
    private Iterator<Bar> converted;

    /**
     * Construct a converter that will aggregate ticks into bars of the given period.
     *
     * @param validator       validator used for model validation
     * @param period          period to aggregate to
     * @param tickInputStream stream of ticks to aggregate
     */
    public TickToBarInputStream(Validator validator,
                                Bar.Period period,
                                TradingInputStream<Tick> tickInputStream) {
        this(validator, period, NO_VISITOR, tickInputStream);
    }

    /**
     * Construct a converter with a {@link BarVisitor} that is invoked for each produced bar.
     *
     * @param validator       validator used for model validation
     * @param period          period to aggregate to
     * @param barVisitor      callback for each generated bar
     * @param tickInputStream stream of ticks to aggregate
     */
    public TickToBarInputStream(Validator validator,
                                Bar.Period period,
                                BarVisitor barVisitor,
                                TradingInputStream<Tick> tickInputStream) {
        delegate = new TickToBarList(validator, period, tickInputStream, barVisitor);
    }


    /**
     * Get the next aggregated bar, computing the conversion on first access.
     */
    @Override
    public Bar next() {
        lazyConvert();
        return converted.next();
    }

    /**
     * Whether another aggregated bar is available.
     */
    @Override
    public boolean hasNext() {
        lazyConvert();
        return converted.hasNext();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
        if (converted != null) {
            converted = null;
        }
    }

    private void lazyConvert() {
        if (converted == null) {
            converted = delegate.convert().iterator();
        }
    }
}
