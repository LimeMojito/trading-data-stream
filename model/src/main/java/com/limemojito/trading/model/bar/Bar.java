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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.limemojito.trading.model.StreamData;
import com.limemojito.trading.model.UtcTimeUtils;
import com.limemojito.trading.model.tick.Tick;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.UUID;
import java.util.function.BinaryOperator;

import static java.time.Instant.ofEpochMilli;

/**
 * Immutable OHLC bar representing aggregated tick data over a fixed period for a single symbol and stream.
 * <p>
 * A bar is defined by its start time (UTC, inclusive), end time (UTC, inclusive), the aggregation {@link Period},
 * the trading {@code symbol}, and the four price points: open, high, low, and close (integer pips/points).
 * The bar also records the {@link StreamSource source} provenance aggregated from its input ticks.
 * </p>
 * <p>
 * Instances are value objects built via Lombok's {@link lombok.Builder} and validated using Jakarta Bean Validation
 * annotations placed on fields. Convenience methods are provided to compute period boundaries and relations between
 * bars, and to expose commonly used temporal representations.
 * </p>
 */
@Value
@Builder(toBuilder = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@SuppressWarnings("RedundantModifiersValueLombok")
public class Bar implements StreamData<Bar> {

    @NotNull
    @Min(0)
    @EqualsAndHashCode.Include
    private final long startMillisecondsUtc;

    @NotNull
    @EqualsAndHashCode.Include
    private final UUID streamId;

    @NotNull
    @EqualsAndHashCode.Include
    private final Period period;

    @NotEmpty
    @Size(min = Tick.SYMBOL_MIN_SIZE)
    @EqualsAndHashCode.Include
    private final String symbol;

    @NotNull
    @Min(1)
    private final int open;

    @NotNull
    @Min(1)
    private final int high;

    @NotNull
    @Min(1)
    private final int low;

    @NotNull
    @Min(1)
    private final int close;

    @NotNull
    private final StreamSource source;

    /**
     * Calculate how many bars of the supplied {@link Period} fit between two instants.
     * The end is exclusive.
     *
     * @param period the bar period to measure with
     * @param start  start time (inclusive)
     * @param end    end time (exclusive)
     * @return number of bars required to cover {@code [start, end)}
     */
    public static long barsIn(Period period, Temporal start, Temporal end) {
        return period.periodsIn(Duration.between(start, end));
    }

    /**
     * Calculate how many bars of the supplied {@link Period} fit in a given {@link Duration}.
     *
     * @param period        the bar period to measure with
     * @param largeDuration the overall duration window
     * @return number of bars that fit within {@code largeDuration}
     */
    public static long barsIn(Period period, Duration largeDuration) {
        return period.periodsIn(largeDuration);
    }

    /**
     * Compute the UTC start millisecond of the bar that contains the supplied epoch time.
     *
     * @param period       target bar period
     * @param milliseconds epoch time in milliseconds (UTC)
     * @return start of the containing bar in epoch milliseconds (UTC)
     */
    public static long startMilliSecondsFor(Period period, long milliseconds) {
        return period.round(milliseconds);
    }

    /**
     * Compute the UTC end millisecond (inclusive) of the bar that contains the supplied epoch time.
     *
     * @param period       target bar period
     * @param milliseconds epoch time in milliseconds (UTC)
     * @return end of the containing bar in epoch milliseconds (UTC), inclusive
     */
    public static long endMilliSecondsFor(Period period, long milliseconds) {
        return period.round(milliseconds) + period.getDurationMilliseconds() - 1L;
    }

    /**
     * Partition key combining stream id, symbol and period. Useful for grouping bars by their origin.
     */
    @Override
    public String getPartitionKey() {
        return getStreamId().toString() + "-" + getSymbol() + "-" + getPeriod();
    }

    /**
     * End of this bar window in epoch milliseconds (UTC), inclusive.
     */
    @JsonIgnore
    public long getEndMillisecondsUtc() {
        return endMilliSecondsFor(period, startMillisecondsUtc);
    }

    /**
     * Start of this bar window as a {@link LocalDateTime} in UTC.
     */
    @JsonIgnore
    public LocalDateTime getStartDateTimeUtc() {
        return UtcTimeUtils.toLocalDateTimeUtc(getStartMillisecondsUtc());
    }

    /**
     * End of this bar window as a {@link LocalDateTime} in UTC.
     */
    @JsonIgnore
    public LocalDateTime getEndDateTimeUtc() {
        return UtcTimeUtils.toLocalDateTimeUtc(getEndMillisecondsUtc());
    }

    /**
     * Start of this bar window as an {@link Instant} (UTC).
     */
    @JsonIgnore
    public Instant getStartInstant() {
        return UtcTimeUtils.toInstant(getStartMillisecondsUtc());
    }

    /**
     * End of this bar window as an {@link Instant} (UTC).
     */
    @JsonIgnore
    public Instant getEndInstant() {
        return UtcTimeUtils.toInstant(getEndMillisecondsUtc());
    }

    /**
     * Natural ordering by stream, then symbol, then period, then start/end time.
     */
    @Override
    public int compareTo(Bar other) {
        int rv = StreamData.compareTo(this, other);
        if (rv == 0) {
            rv = symbol.compareTo(other.getSymbol());
            if (rv == 0) {
                rv = Integer.compare(period.ordinal(), other.period.ordinal());
                if (rv == 0) {
                    rv = Long.compare(getStartMillisecondsUtc(), other.getStartMillisecondsUtc());
                    if (rv == 0) {
                        rv = Long.compare(getEndMillisecondsUtc(), other.getEndMillisecondsUtc());
                    }
                }
            }
        }
        return rv;
    }

    /**
     * Whether this bar belongs to the same stream and symbol as the other bar.
     * Does not compare period or time window.
     */
    public boolean isInSameStream(Bar other) {
        return streamId.equals(other.getStreamId()) && symbol.equals(other.getSymbol());
    }

    /**
     * True if this bar lies entirely within the time window of the supplied bar and shares the same stream+symbol.
     * The supplied bar must be the same or a larger period than this bar.
     */
    public boolean within(Bar biggerBar) {
        return isInSameStream(biggerBar)
               && biggerBar.period.ordinal() >= period.ordinal()
               && biggerBar.getStartMillisecondsUtc() <= getStartMillisecondsUtc()
               && biggerBar.getEndMillisecondsUtc() >= getEndMillisecondsUtc();
    }

    /**
     * True if this bar entirely surrounds the time window of the supplied bar and shares the same stream+symbol.
     * The supplied bar must be the same or a smaller period than this bar.
     */
    public boolean surrounds(Bar smallerBar) {
        return isInSameStream(smallerBar)
               && smallerBar.period.ordinal() <= period.ordinal()
               && smallerBar.getStartMillisecondsUtc() >= getStartMillisecondsUtc()
               && smallerBar.getEndMillisecondsUtc() <= getEndMillisecondsUtc();
    }

    /**
     * Supported aggregation periods for bars.
     * Each constant stores its {@link Duration}, and helper methods are provided to perform
     * rounding and period arithmetic.
     */
    @RequiredArgsConstructor
    @Getter
    public enum Period {
        M5(Duration.ofMinutes(5)),
        M10(Duration.ofMinutes(10)),
        M15(Duration.ofMinutes(15)),
        M30(Duration.ofMinutes(30)),
        H1(Duration.ofHours(1)),
        H4(Duration.ofHours(4)),
        D1(Duration.ofDays(1));

        @JsonIgnore
        private final Duration duration;

        /**
         * Return the smallest (finest) period from the given collection.
         *
         * @param periods collection of periods (must not be empty)
         * @return the period with the smallest duration
         * @throws IllegalStateException if the collection is empty
         */
        public static Period smallest(Collection<Period> periods) {
            return reduce(periods, (a, b) -> a.ordinal() < b.ordinal() ? a : b);
        }

        /**
         * Return the largest (coarsest) period from the given collection.
         *
         * @param periods collection of periods (must not be empty)
         * @return the period with the largest duration
         * @throws IllegalStateException if the collection is empty
         */
        public static Period largest(Collection<Period> periods) {
            return reduce(periods, (a, b) -> a.ordinal() > b.ordinal() ? a : b);
        }

        @JsonIgnore
        public long getDurationMilliseconds() {
            return duration.toMillis();
        }

        /**
         * Compute the number of periods in a larger period
         *
         * @param largerPeriod the period to see how much of our period size is contained in.
         * @return number of periods.
         */
        public int periodsIn(Period largerPeriod) {
            return (int) periodsIn(largerPeriod.duration);
        }

        /**
         * Compute the number of periods between times.
         * Note for a M5 this would be between "2018-07-06T12:00:00Z", "2018-07-06T12:05:00Z" == 1 for example.
         *
         * @param start The start of the period
         * @param end   The end of the period (exclusive)
         * @return The number of bars required to fill the period.
         */
        public long periodsIn(Temporal start, Temporal end) {
            return periodsIn(Duration.between(start, end));
        }

        /**
         * Compute the number of periods between times.
         *
         * @param largeDuration The duration of the larger period.
         * @return The number of periods required to fill the duration.
         */
        public long periodsIn(Duration largeDuration) {
            return Math.max(largeDuration.getSeconds() / duration.getSeconds(), 0);
        }

        /**
         * Rounds supplied time to the start of the period.
         *
         * @param time Start time to round.
         * @return Rounded start of period time.
         */
        public Instant round(Instant time) {
            final long epochMilliSeconds = time.toEpochMilli();
            final long startMillisM5 = round(epochMilliSeconds);
            return ofEpochMilli(startMillisM5);
        }

        /**
         * Rounds supplied time to the start of the period.
         *
         * @param epochMilliSeconds Start time to round in Unix Epoch milliseconds.
         * @return Rounded start of period time.
         */
        public long round(long epochMilliSeconds) {
            final long periodMillis = getDurationMilliseconds();
            return ((epochMilliSeconds / periodMillis) * periodMillis);
        }

        private static Period reduce(Collection<Period> periods, BinaryOperator<Period> periodBinaryOperator) {
            if (periods.isEmpty()) {
                throw new IllegalStateException("Supplied period list must not be empty");
            }
            return periods.stream().reduce(periods.iterator().next(), periodBinaryOperator);
        }

    }
}
