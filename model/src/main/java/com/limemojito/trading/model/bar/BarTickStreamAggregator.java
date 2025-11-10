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

import com.limemojito.trading.model.StreamData.StreamSource;
import com.limemojito.trading.model.UtcTimeUtils;
import com.limemojito.trading.model.tick.Tick;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.Getter;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static com.limemojito.trading.model.StreamData.StreamSource.Live;
import static java.lang.String.format;
import static java.util.Collections.emptySet;

/**
 * Mutable builder that aggregates a stream of {@link com.limemojito.trading.model.tick.Tick} values
 * into a single {@link Bar} for a specific stream id, symbol and period window.
 * <p>
 * Instances are stateful and not thread-safe for concurrent adds; external synchronization is required
 * if used across threads. Construct a new instance for each distinct bar time window.
 * </p>
 */
public class BarTickStreamAggregator {

    private final Validator validator;
    @Getter
    private final long endMillisecondsUtc;
    @Getter
    private final String symbol;
    @Getter
    private final long startMillisecondsUtc;
    @Getter
    private final Bar.Period period;
    @Getter
    private final UUID streamId;
    /**
     * Value based on bid value from tick addition.
     */
    private int low;
    /**
     * Value based on bid value from tick addition.
     */
    private int high;
    /**
     * Value based on bid value from tick addition.
     */
    private int open;
    /**
     * Value based on bid value from tick addition.
     */
    private int close;
    /**
     * Number of tick records in bar calculation
     */
    private int tickVolume;
    private StreamSource source;

    /**
     * Construct an aggregator for a specific stream id, symbol and period starting at the given instant.
     *
     * @param validator            validator used to validate ticks and produced bars
     * @param streamId             originating stream id
     * @param symbol               trading symbol
     * @param startMillisecondsUtc start instant of the bar window (UTC)
     * @param period               aggregation period
     */
    public BarTickStreamAggregator(Validator validator,
                                   UUID streamId,
                                   String symbol,
                                   Instant startMillisecondsUtc,
                                   Bar.Period period) {
        this(validator, streamId, symbol, startMillisecondsUtc.toEpochMilli(), period);
    }

    /**
     * Construct an aggregator for a specific stream id, symbol and period starting at the given instant.
     *
     * @param validator            validator used to validate ticks and produced bars
     * @param streamId             originating stream id
     * @param symbol               trading symbol
     * @param startMillisecondsUtc start instant of the bar window (UTC)
     * @param period               aggregation period
     */
    public BarTickStreamAggregator(Validator validator,
                                   UUID streamId,
                                   String symbol,
                                   long startMillisecondsUtc,
                                   Bar.Period period) {
        this.streamId = streamId;
        this.symbol = symbol;
        this.period = period;
        this.validator = validator;
        this.startMillisecondsUtc = Bar.startMilliSecondsFor(period, startMillisecondsUtc);
        this.endMillisecondsUtc = Bar.endMilliSecondsFor(period, startMillisecondsUtc);
        this.source = Live;
    }

    public Instant getStartDateInstant() {
        return UtcTimeUtils.toInstant(startMillisecondsUtc);
    }

    public Instant getEndDateInstant() {
        return UtcTimeUtils.toInstant(endMillisecondsUtc);
    }

    public Bar toBar() {
        final Bar bar = Bar.builder()
                           .streamId(streamId)
                           .symbol(symbol)
                           .startMillisecondsUtc(startMillisecondsUtc)
                           .period(period)
                           .open(open)
                           .close(close)
                           .high(high)
                           .low(low)
                           .source(source)
                           .build();
        validate(bar);
        return bar;
    }

    /**
     * Aggregates a tick into this bar.
     *
     * @param tick Tick to add to this bar
     * @throws ConstraintViolationException if the supplied tick violates model constraints.
     */
    public synchronized void add(Tick tick) {
        checkPreconditions(tick);
        final int value = tick.getBid();
        if (tickVolume == 0) {
            open = value;
            low = value;
            high = value;
        } else {
            if (value < low) {
                low = value;
            }
            if (value > high) {
                high = value;
            }
        }
        source = StreamSource.aggregate(source, tick.getSource());
        close = value;
        tickVolume++;
    }

    private void checkPreconditions(Tick tick) {
        validate(tick);
        if (!streamId.equals(tick.getStreamId())) {
            throw new ConstraintViolationException(format("Tick %s %s (%d) is not part of stream %s",
                                                          tick.getSymbol(),
                                                          tick.getInstant(),
                                                          tick.getMillisecondsUtc(),
                                                          streamId),
                                                   emptySet());
        }
        if (tick.getMillisecondsUtc() > endMillisecondsUtc) {
            throw new ConstraintViolationException(format("Tick %s %s (%d) is past end of bar %s (%d)",
                                                          tick.getSymbol(),
                                                          tick.getInstant(),
                                                          tick.getMillisecondsUtc(),
                                                          getEndDateInstant(),
                                                          getEndMillisecondsUtc()),
                                                   emptySet());
        }
        if (tick.getMillisecondsUtc() < startMillisecondsUtc) {
            throw new ConstraintViolationException(format("Tick %s %s (%d) is before start of bar %s (%d)",
                                                          tick.getSymbol(),
                                                          tick.getInstant(),
                                                          tick.getMillisecondsUtc(),
                                                          getStartDateInstant(),
                                                          getStartMillisecondsUtc()),
                                                   emptySet());
        }
        if (!tick.getSymbol().equals(symbol)) {
            throw new ConstraintViolationException(format("Tick %s %s (%d) is not matching bar symbol %s",
                                                          tick.getSymbol(),
                                                          tick.getInstant(),
                                                          tick.getMillisecondsUtc(),
                                                          getSymbol()),
                                                   emptySet());
        }
    }

    private <T> void validate(T object) {
        final Set<ConstraintViolation<T>> constraintViolations = validator.validate(object);
        if (!constraintViolations.isEmpty()) {
            throw new ConstraintViolationException(constraintViolations);
        }
    }
}
