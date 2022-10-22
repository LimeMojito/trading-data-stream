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

package com.limemojito.trading.dukascopy;

import com.limemojito.trading.Bar;
import com.limemojito.trading.BarVisitor;
import com.limemojito.trading.Tick;
import com.limemojito.trading.TickToBarList;
import com.limemojito.trading.TradingInputStream;
import lombok.SneakyThrows;

import javax.validation.Validator;
import java.io.IOException;
import java.util.Iterator;

import static com.limemojito.trading.BarVisitor.NO_VISITOR;

/**
 * Note that this class is dangerous to use with large tick input streams as it will perform a bar aggregation on the first object request.
 * This will read all the supplied ticks and generate bars of the appropriate period in memory.
 */
public class DukascopyBarInputStream implements TradingInputStream<Bar> {
    private final TickToBarList delegate;
    private Iterator<Bar> converted;

    /**
     * Note that this method is dangerous to use with large tick input streams as it will perform a bar aggregation on the first object request.
     * This will read all the supplied ticks and generate bars of the appropriate period in memory.
     *
     * @param validator       to validate objects
     * @param period          period to aggregate to
     * @param tickInputStream stream to aggregate
     */
    public DukascopyBarInputStream(Validator validator, Bar.Period period, TradingInputStream<Tick> tickInputStream) {
        this(validator, period, NO_VISITOR, tickInputStream);
    }

    /**
     * Note that this method is dangerous to use with large tick input streams as it will perform a bar aggregation on the first object request.
     * This will read all the supplied ticks and generate bars of the appropriate period in memory.
     *
     * @param validator       to validate objects
     * @param period          period to aggregate to
     * @param barVisitor      visit to occur on each bar generated.
     * @param tickInputStream stream to aggregate
     */
    public DukascopyBarInputStream(Validator validator, Bar.Period period, BarVisitor barVisitor, TradingInputStream<Tick> tickInputStream) {
        delegate = new TickToBarList(validator, period, tickInputStream, barVisitor);
    }


    @Override
    @SneakyThrows
    public Bar next() {
        lazyConvert();
        return converted.next();
    }

    @Override
    @SneakyThrows
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
