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

import com.limemojito.trading.model.StreamData;
import com.limemojito.trading.model.bar.Bar.Period;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.limemojito.trading.model.StreamData.REALTIME_UUID;
import static java.lang.String.format;

/**
 * Aggregates bars from a smaller period (e.g., M5) into a larger period (e.g., H1) in descending time order.
 * The input list must be sorted in descending time (most recent first) and all bars must share the same
 * symbol and period. The output bars preserve descending order and compute OHLC by folding constituent bars.
 */
@RequiredArgsConstructor
public class SmallToLargeBarAggregator {
    private final Validator validator;

    /**
     * Produce a list of bars by aggregating the supplied bars onto the target period.  The input list must be sorted in
     * descending time (most recent first) and all bars must share the same
     * symbol and period. The output bars preserve descending order and compute OHLC by folding constituent bars.
     *
     * @param targetPeriod larger than the supplied bars
     * @param smallerBars  Data to add to a larger period sorted in descending time.
     * @return The new list of bars.
     */
    public List<Bar> aggregate(Period targetPeriod, List<Bar> smallerBars) {
        if (smallerBars.isEmpty()) {
            return Collections.emptyList();
        }
        if (smallerBars.size() == 1) {
            return aggregateOneBar(targetPeriod, smallerBars.get(0));
        }
        return aggregateTwoPlusBars(targetPeriod, smallerBars, smallerBars.get(0));
    }

    private List<Bar> aggregateOneBar(Period targetPeriod, Bar first) {
        validateNextBar(targetPeriod, first.getSymbol(), first.getPeriod(), null, first);
        long barStartMillis = Bar.startMilliSecondsFor(targetPeriod, first.getStartMillisecondsUtc());
        return List.of(createBar(first.getSymbol(),
                                 targetPeriod,
                                 barStartMillis,
                                 first.getOpen(),
                                 first.getHigh(),
                                 first.getLow(),
                                 first.getClose(),
                                 first.getSource()
                                ));
    }

    @SuppressWarnings("JavaNCSS")
    private List<Bar> aggregateTwoPlusBars(Period targetPeriod, List<Bar> smallerBars, Bar first) {
        final String symbol = first.getSymbol();
        final List<Bar> aggregated = new ArrayList<>();
        // set to initial values of the first row
        long barStartMillis = Bar.startMilliSecondsFor(targetPeriod, first.getStartMillisecondsUtc());
        int close = first.getClose();
        int open = first.getOpen();
        StreamData.StreamSource source = first.getSource();
        Bar lastBar = null;
        int high = Integer.MIN_VALUE;
        int low = Integer.MAX_VALUE;
        for (Bar nextBar : smallerBars) {
            validateNextBar(targetPeriod, first.getSymbol(), first.getPeriod(), lastBar, nextBar);
            if (isNewStartBar(barStartMillis, lastBar, nextBar)) {
                // open should be the last bar in the new period.
                aggregated.add(createBar(symbol, targetPeriod, barStartMillis, open, high, low, close, source));
                // close should be the first bar in the new period as we are descending.
                barStartMillis = Bar.startMilliSecondsFor(targetPeriod, nextBar.getStartMillisecondsUtc());
                close = nextBar.getClose();
                high = Integer.MIN_VALUE;
                low = Integer.MAX_VALUE;
                source = nextBar.getSource();
            }
            open = nextBar.getOpen();
            high = Math.max(high, nextBar.getHigh());
            low = Math.min(low, nextBar.getLow());
            source = StreamData.StreamSource.aggregate(source, nextBar.getSource());

            lastBar = nextBar;
        }
        // close it off.
        aggregated.add(createBar(symbol, targetPeriod, barStartMillis, open, high, low, close, source));
        return aggregated;
    }

    private boolean isNewStartBar(long barStartMillis, Bar lastBar, Bar nextBar) {
        return (lastBar != null) && barStartMillis > nextBar.getEndMillisecondsUtc();
    }

    private void validateNextBar(Period targetPeriod, String symbol, Period smallPeriod, Bar lastBar, Bar smallerBar) {
        if (!symbol.equals(smallerBar.getSymbol())) {
            throw new IllegalArgumentException(format(
                    "BarAggregator does not support bars with different symbols.  First was %s",
                    symbol));
        }
        if (targetPeriod.compareTo(smallerBar.getPeriod()) <= 0) {
            throw new IllegalArgumentException(format(
                    "BarAggregator does not support bars with larger periods %s that the target %s.",
                    smallerBar.getPeriod(),
                    targetPeriod));
        }
        if (smallPeriod.compareTo(smallerBar.getPeriod()) != 0) {
            throw new IllegalArgumentException(format(
                    "BarAggregator does not support bars with mixed periods.  First was %s.",
                    smallPeriod));
        }
        if (lastBar != null && lastBar.getStartMillisecondsUtc() < smallerBar.getStartMillisecondsUtc()) {
            throw new IllegalArgumentException("BarAggregator requires bars sorted in descending time");
        }
    }

    @SuppressWarnings("ParameterNumber")
    private Bar createBar(String symbol,
                          Period targetPeriod,
                          long barStartMillis,
                          int open, int high,
                          int low,
                          int close,
                          StreamData.StreamSource source) {
        // we have a new bar
        final Bar bar = Bar.builder()
                           .streamId(REALTIME_UUID)
                           .symbol(symbol)
                           .period(targetPeriod)
                           .startMillisecondsUtc(barStartMillis)
                           .source(source)
                           .open(open)
                           .close(close)
                           .high(high)
                           .low(low)
                           .build();
        final Set<ConstraintViolation<Bar>> validate = validator.validate(bar);
        if (!validate.isEmpty()) {
            throw new ConstraintViolationException(validate);
        }
        return bar;
    }
}
