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

package com.limemojito.trading.model.tick;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.limemojito.trading.model.StreamData;
import com.limemojito.trading.model.UtcTimeUtils;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable market tick representing bid/ask prices and volumes at a specific UTC instant for a symbol and stream.
 * <p>
 * Instances are comparable and can be partitioned by {@link #getPartitionKey()} which combines stream id and symbol.
 * Convenience accessors expose both {@link java.time.LocalDateTime} and {@link java.time.Instant} views of the
 * {@code millisecondsUtc} timestamp.
 * </p>
 */
@Value
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@SuppressWarnings("RedundantModifiersValueLombok")
public class Tick implements StreamData<Tick> {
    public static final int SYMBOL_MIN_SIZE = 6;
    @NotNull
    @Min(0)
    @EqualsAndHashCode.Include
    private final long millisecondsUtc;

    @NotNull
    @EqualsAndHashCode.Include
    private final UUID streamId;

    @NotNull
    @Size(min = SYMBOL_MIN_SIZE)
    @EqualsAndHashCode.Include
    private final String symbol;

    /**
     * This is asking price for parcel size, so 100,000 for Forex
     */
    @NotNull
    @Min(1)
    private final int ask;

    /**
     * This is asking price for parcel size, so 100,000 for Forex
     */
    @NotNull
    @Min(1)
    private final int bid;

    /**
     * Volume of ask in the liquidity pool, in millions. (ie 1.23 is 1,230,000)
     */
    @NotNull
    @Min(0)
    private final float askVolume;

    /**
     * Volume of bid in the liquidity pool, in millions. (ie 1.23 is 1,230,000)
     */
    @NotNull
    @Min(0)
    private final float bidVolume;

    @NotNull
    private final StreamSource source;

    /**
     * Convenience accessor returning the timestamp as a UTC {@link LocalDateTime} suitable for CSV output.
     */
    @JsonIgnore
    public LocalDateTime getDateTimeUtc() {
        return UtcTimeUtils.toLocalDateTimeUtc(getMillisecondsUtc());
    }

    /**
     * Convenience accessor returning the timestamp as an {@link Instant}.
     */
    @JsonIgnore
    public Instant getInstant() {
        return UtcTimeUtils.toInstant(getMillisecondsUtc());
    }

    /**
     * Partition key combining stream id and symbol, useful for sharding.
     */
    @Override
    public String getPartitionKey() {
        return getStreamId().toString() + "-" + getSymbol();
    }

    /**
     * True when both ticks belong to the same logical stream (same stream id and symbol).
     */
    @Override
    public boolean isInSameStream(Tick other) {
        return getStreamId().equals(other.getStreamId()) && getSymbol().equals(other.getSymbol());
    }

    /**
     * Natural ordering: first by {@link StreamData#compareTo(StreamData)} contract, then by symbol, then by timestamp.
     */
    @Override
    public int compareTo(Tick other) {
        int rv = StreamData.compareTo(this, other);
        if (rv == 0) {
            rv = symbol.compareTo(other.symbol);
            if (rv == 0) {
                rv = Long.compare(millisecondsUtc, other.millisecondsUtc);
            }
        }
        return rv;
    }
}
