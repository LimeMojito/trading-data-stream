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

    @JsonIgnore
    public LocalDateTime getDateTimeUtc() {
        return UtcTimeUtils.toLocalDateTimeUtc(getMillisecondsUtc());
    }

    @JsonIgnore
    public Instant getInstant() {
        return UtcTimeUtils.toInstant(getMillisecondsUtc());
    }

    @Override
    public String getPartitionKey() {
        return getStreamId().toString() + "-" + getSymbol();
    }

    @Override
    public boolean isInSameStream(Tick other) {
        return getStreamId().equals(other.getStreamId()) && getSymbol().equals(other.getSymbol());
    }

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
