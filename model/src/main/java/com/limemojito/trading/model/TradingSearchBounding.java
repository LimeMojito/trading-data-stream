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

/**
 * Bounds on search space and instant validation.
 */
public interface TradingSearchBounding {
    /**
     * Sets the limit of searching.
     *
     * @param theBeginningOfTime past this point searches will end data.
     */
    void setTheBeginningOfTime(Instant theBeginningOfTime);

    /**
     * Gets the limit of searching.
     *
     * @return The point where searches past will stop
     */
    Instant getTheBeginningOfTime();

    /**
     * Check that the supplied time field is after the beginning of time.
     *
     * @param instant   the time to check
     * @param fieldName the name of the field being checked
     * @see #getTheBeginningOfTime
     */
    void assertCriteriaTime(Instant instant, String fieldName);

    /**
     * Checks that the criteria times are valid and span a range.
     *
     * @param startTime the instant that the search starts at.
     * @param endTime   the instant that the search ends at.
     */
    void assertCriteriaTimes(Instant startTime, Instant endTime);
}
