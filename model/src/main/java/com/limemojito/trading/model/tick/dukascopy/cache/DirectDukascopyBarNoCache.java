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

package com.limemojito.trading.model.tick.dukascopy.cache;

import com.limemojito.trading.model.TradingInputStream;
import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.bar.TickToBarList;
import com.limemojito.trading.model.tick.Tick;
import com.limemojito.trading.model.tick.dukascopy.DukascopyCache;
import com.limemojito.trading.model.tick.dukascopy.DukascopyTickSearch;
import com.limemojito.trading.model.tick.dukascopy.criteria.BarCriteria;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.limemojito.trading.model.tick.TickVisitor.NO_VISITOR;

@RequiredArgsConstructor
@Slf4j
public class DirectDukascopyBarNoCache implements DukascopyCache.BarCache {
    private final Validator validator;
    private final DukascopyTickSearch tickSearch;
    private final AtomicInteger retrieveCount = new AtomicInteger();

    public List<Bar> getOneDayOfTicksAsBar(BarCriteria criteria, List<String> dayOfPaths) throws IOException {
        final int supportedSize = 24;
        if (dayOfPaths.size() > supportedSize) {
            throw new IllegalArgumentException("Paths for Day of 1H Tick files is not 24! " + dayOfPaths.size());
        }
        log.info("Retrieving {} {} {} -> {} as direct Dukascopy tick fetch",
                 criteria.getSymbol(),
                 criteria.getPeriod(),
                 criteria.getDayStart(),
                 criteria.getDayEnd());
        try (TradingInputStream<Tick> dayOfTicks = tickSearch.search(criteria.getSymbol(),
                                                                     dayOfPaths,
                                                                     tick -> true,
                                                                     NO_VISITOR);
             TickToBarList tickToBarList = new TickToBarList(validator, criteria.getPeriod(), dayOfTicks)) {
            List<Bar> bars = tickToBarList.convert();
            retrieveCount.addAndGet(dayOfPaths.size());
            log.info("Retrieved {} bars", bars.size());
            return bars;
        }
    }

    @Override
    public int getHitCount() {
        return 0;
    }

    @Override
    public int getMissCount() {
        return getRetrieveCount();
    }

    @Override
    public int getRetrieveCount() {
        return retrieveCount.get();
    }

    @Override
    public String cacheStats() {
        return String.format("DirectBarNoCache: %d day retrieve(s)", getRetrieveCount());
    }
}
