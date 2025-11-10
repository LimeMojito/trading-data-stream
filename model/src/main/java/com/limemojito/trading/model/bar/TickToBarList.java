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
import com.limemojito.trading.model.bar.Bar.Period;
import com.limemojito.trading.model.tick.Tick;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility that consumes a {@link com.limemojito.trading.model.TradingInputStream} of {@link com.limemojito.trading.model.tick.Tick}
 * and produces a {@link java.util.List} of {@link Bar} by aggregating ticks into the requested period.
 * Bars are visited via an optional {@link BarVisitor} as they are produced.
 */
@Slf4j
public class TickToBarList implements AutoCloseable {
    private final TradingInputStream<Tick> dukascopyInputStream;
    private final Period period;
    private final BarVisitor visitor;
    private final Validator validator;

    /**
     * Construct a converter that aggregates the supplied tick stream into bars of the given period.
     * No visitor is invoked for produced bars.
     *
     * @param validator       validator used for model validation
     * @param period          aggregation period to produce
     * @param tickInputStream input tick stream
     */
    public TickToBarList(Validator validator, Period period, TradingInputStream<Tick> tickInputStream) {
        this(validator, period, tickInputStream, BarVisitor.NO_VISITOR);
    }

    /**
     * Construct a converter with an explicit {@link BarVisitor} callback that will be invoked for each produced bar.
     *
     * @param validator       validator used for model validation
     * @param period          aggregation period to produce
     * @param tickInputStream input tick stream
     * @param visitor         callback to invoke per bar (may be {@link BarVisitor#NO_VISITOR})
     */
    public TickToBarList(Validator validator,
                         Period period,
                         TradingInputStream<Tick> tickInputStream,
                         BarVisitor visitor) {
        this.period = period;
        this.visitor = visitor;
        this.validator = validator;
        this.dukascopyInputStream = tickInputStream;
    }

    /**
     * Convert the entire tick input stream into a list of bars for the configured period.
     * The underlying stream is fully consumed; the returned list is in the original stream order.
     *
     * @return list of aggregated bars
     */
    public List<Bar> convert() {
        final List<Bar> barList = new ArrayList<>();
        final TickBarNotifyingAggregator aggregator = new TickBarNotifyingAggregator(validator,
                                                                                     bar -> newBar(barList, bar),
                                                                                     period);
        aggregator.loadStart();
        while (dukascopyInputStream.hasNext()) {
            final Tick tick = dukascopyInputStream.next();
            aggregator.add(tick);
        }
        aggregator.loadEnd();
        return barList;
    }

    /**
     * Close the underlying tick stream and release resources.
     */
    @Override
    public void close() throws IOException {
        dukascopyInputStream.close();
    }

    private void newBar(List<Bar> barList, Bar bar) {
        barList.add(bar);
        visitor.visit(bar);
    }
}
