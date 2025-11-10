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

package com.limemojito.trading.model;

import java.time.Instant;
import java.time.LocalDateTime;

import static java.time.ZoneOffset.UTC;

/**
 * Small helpers for converting to/from UTC-based epoch milliseconds.
 */
public class UtcTimeUtils {

    /**
     * Converts a UTC {@link LocalDateTime} to epoch milliseconds.
     * <p>
     * Note: the input is interpreted at {@link java.time.ZoneOffset#UTC}.
     *
     * @param dateTime a UTC local date-time
     * @return epoch milliseconds since 1970-01-01T00:00:00Z
     */
    public static long toEpochMillis(LocalDateTime dateTime) {
        return toEpochMillis(dateTime.toInstant(UTC));
    }

    /**
     * Converts an {@link Instant} to epoch milliseconds.
     *
     * @param instant an instant in time
     * @return epoch milliseconds since 1970-01-01T00:00:00Z
     */
    public static long toEpochMillis(Instant instant) {
        return instant.toEpochMilli();
    }

    /**
     * Converts epoch milliseconds to a UTC {@link LocalDateTime}.
     *
     * @param epochMillis epoch milliseconds since 1970-01-01T00:00:00Z
     * @return a UTC local date-time
     */
    public static LocalDateTime toLocalDateTimeUtc(long epochMillis) {
        return toInstant(epochMillis).atZone(UTC).toLocalDateTime();
    }

    /**
     * Creates an {@link Instant} from epoch milliseconds.
     *
     * @param epochMillis epoch milliseconds since 1970-01-01T00:00:00Z
     * @return an instant
     */
    public static Instant toInstant(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis);
    }
}
