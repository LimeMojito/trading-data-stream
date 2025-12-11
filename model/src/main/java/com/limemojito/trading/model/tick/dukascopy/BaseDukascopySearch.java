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

package com.limemojito.trading.model.tick.dukascopy;

import com.limemojito.trading.model.TradingSearchBounding;
import com.limemojito.trading.model.tick.dukascopy.criteria.Criteria;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Base functionality shared by Dukascopy search implementations.
 * <p>
 * Provides a common beginning-of-time guard and criteria validation helper used
 * by both tick and bar search classes.
 * </p>
 */
public class BaseDukascopySearch implements TradingSearchBounding {
    /**
     * Defaulting the beginning of Dukascopy searches to be 2020.  This puts a limit on recursive searching.
     */
    public static final String DEFAULT_BEGINNING_OF_TIME = "2020-01-01T00:00:00Z";

    @Setter
    @Getter
    private Instant theBeginningOfTime = Instant.parse(DukascopySearch.DEFAULT_BEGINNING_OF_TIME);

    @Override
    public void assertCriteriaTimes(Instant startTime, Instant endTime) {
        assertCriteriaTime(startTime, "Start");
        assertCriteriaTime(endTime, "End");
        Criteria.assertBeforeStart(startTime, endTime);
    }

    @Override
    public void assertCriteriaTime(Instant instant, String fieldName) {
        if (instant.isBefore(theBeginningOfTime)) {
            throw new IllegalArgumentException(String.format("%s %s must be after %s",
                                                             fieldName,
                                                             instant,
                                                             theBeginningOfTime));
        }
    }
}
