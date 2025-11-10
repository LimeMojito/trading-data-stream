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

package com.limemojito.trading.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Common contract for models that belong to a logical data stream (e.g. ticks, bars).
 * <p>
 * Implementations carry a {@link #getStreamId()} that identifies whether the data item belongs to
 * a realtime or a backtest stream and allow callers to derive a stable partition key for
 * parallel processing while preserving ordering per stream.
 *
 * @param <DataType> the concrete stream data type used for comparisons
 */
public interface StreamData<DataType extends StreamData<?>> extends Comparable<DataType> {
    /**
     * Reserved UUID that marks items as belonging to the realtime stream.
     */
    UUID REALTIME_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    /**
     * Compare two stream-aware items by their stream identity only.
     *
     * @param me    the left item
     * @param other the right item
     * @return 0 if both belong to the same stream; otherwise the ordering of the stream types
     */
    static int compareTo(StreamData<?> me, StreamData<?> other) {
        return compare(me.getStreamId(), other.getStreamId());
    }

    /**
     * Checks whether a given stream id represents the realtime stream.
     *
     * @param streamId stream identifier
     * @return true when {@code streamId} equals {@link #REALTIME_UUID}
     */
    static boolean isRealtime(UUID streamId) {
        return REALTIME_UUID.equals(streamId);
    }

    /**
     * Checks whether a given stream id represents a backtest stream.
     *
     * @param streamId stream identifier
     * @return true when the id is not {@link #REALTIME_UUID}
     */
    static boolean isBacktest(UUID streamId) {
        return !isRealtime(streamId);
    }

    /**
     * Compares two stream identifiers by their type (realtime vs backtest).
     *
     * @param streamId left id
     * @param other    right id
     * @return comparison value consistent with {@link Comparable}
     */
    static int compare(UUID streamId, UUID other) {
        return streamId.equals(other) ? 0 : type(streamId).compareTo(type(other));
    }

    /**
     * Classifies a stream id into a {@link StreamType}.
     *
     * @param uid a stream identifier
     * @return {@link StreamType#Realtime} when {@code uid} equals {@link #REALTIME_UUID}, otherwise {@link StreamType#Backtest}
     */
    static StreamType type(UUID uid) {
        return REALTIME_UUID.equals(uid) ? StreamType.Realtime : StreamType.Backtest;
    }

    /**
     * Unique identifier for the stream this item belongs to.
     */
    UUID getStreamId();

    /**
     * Origin of the data item (e.g. live feed or historical archive).
     */
    StreamSource getSource();

    /**
     * Version of the model contract for clients/serialization. Exposed as read-only JSON field.
     *
     * @return a semantic version string for the model
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    default String getModelVersion() {
        return ModelVersion.VERSION;
    }

    /**
     * A string representing the partition key to uses with these objects for streams, parallel processing, etc.
     *
     * @return A string key that safely partitions the stream into ordered objects with stream identity.
     */
    @JsonIgnore
    String getPartitionKey();

    /**
     * Convenience accessor for the {@link StreamType} of {@link #getStreamId()}.
     * Marked with {@link JsonIgnore} to avoid redundant serialization.
     *
     * @return the stream type for this item
     */
    @JsonIgnore
    default StreamType getStreamType() {
        return type(getStreamId());
    }

    /**
     * Indicates whether another item belongs to the same logical stream instance as this one.
     *
     * @param other another stream data item
     * @return true when both items share the same {@link #getStreamId()}
     */
    boolean isInSameStream(DataType other);

    /**
     * Natural ordering for items of the same concrete data type.
     * Implementations must be consistent with equals when applicable.
     */
    @SuppressWarnings("NullableProblems")
    int compareTo(DataType other);

    /**
     * Classification of stream identity.
     */
    enum StreamType {
        /** Backtest stream identity. */
        Backtest,
        /** Realtime stream identity. */
        Realtime
    }

    /**
     * Origin of a data item.
     */
    enum StreamSource {
        /** Live market data. */
        Live,
        /** Historical (archived) data. */
        Historical;

        /**
         * If we are combining sources then Live is displaced by historical data. We value Live more
         * but Historical contaminates Live.
         *
         * @param left  Source on the left
         * @param right Source on the right
         * @return Outcome when combined.
         */
        public static StreamSource aggregate(StreamSource left, StreamSource right) {
            if ((left == Live && right == Historical)) {
                return Historical;
            }
            return left;
        }
    }
}
