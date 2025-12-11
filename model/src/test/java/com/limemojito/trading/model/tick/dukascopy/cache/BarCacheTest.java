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

package com.limemojito.trading.model.tick.dukascopy.cache;

import com.limemojito.trading.model.MarketStatus;
import com.limemojito.trading.model.ModelPrototype;
import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.tick.dukascopy.DukascopyCache;
import com.limemojito.trading.model.tick.dukascopy.DukascopyPathGenerator;
import com.limemojito.trading.model.tick.dukascopy.DukascopyTickSearch;
import com.limemojito.trading.model.tick.dukascopy.DukascopyUtils;
import com.limemojito.trading.model.tick.dukascopy.criteria.BarCriteria;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.limemojito.trading.model.bar.Bar.Period.M10;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
public class BarCacheTest {
    private final Validator validator = DukascopyUtils.setupValidator();
    private final JsonMapper mapper = DukascopyUtils.setupObjectMapper();
    private final DukascopyPathGenerator pathGenerator = new DukascopyPathGenerator(new MarketStatus());
    private final BarCriteria criteria = new BarCriteria("EURUSD",
                                                         M10,
                                                         Instant.parse("2019-06-07T04:00:00Z"),
                                                         Instant.parse("2019-06-07T05:00:00Z"));
    private final List<String> paths = pathGenerator.generatePaths(criteria.getSymbol(),
                                                                   criteria.getDayStart(0),
                                                                   criteria.getDayEnd(0));
    private final DukascopyTickSearch tickSearch = new DukascopyTickSearch(validator,
                                                                           new DirectDukascopyNoCache(),
                                                                           pathGenerator);

    @Test
    public void shouldProduceCacheStatsForDirect() throws Exception {
        final DirectDukascopyBarNoCache directBarNoCache = new DirectDukascopyBarNoCache(validator, tickSearch);
        assertBarsRetrieved(directBarNoCache);
        // this has skipped market closed days.
        assertThat(directBarNoCache.getCacheStatistics()
                                   .cacheStats()).isEqualTo("DirectDukascopyBarNoCache: retrieve: 1, hit: 0, miss: 1");
        assertThat(directBarNoCache.getCacheStatistics().getHitCount()).isEqualTo(0);
        assertThat(directBarNoCache.getCacheStatistics().getMissCount()).isEqualTo(directBarNoCache.getCacheStatistics()
                                                                                                   .getRetrieveCount());
        assertThat(directBarNoCache.getCacheStatistics().getMissCount()).isEqualTo(1);
        assertThat(directBarNoCache.getCacheStatistics().getHitRate()).isEqualTo(0.0);
    }

    @Test
    public void shouldFailIfMoreThan24barsSupplied() {
        final DirectDukascopyBarNoCache directBarNoCache = new DirectDukascopyBarNoCache(validator, tickSearch);
        List<String> extraPaths = new ArrayList<>(paths);
        extraPaths.addAll(paths);
        assertThatThrownBy(() -> directBarNoCache.getOneDayOfTicksAsBar(criteria, extraPaths))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Paths for Day of 1H Tick files is not 24! " + extraPaths.size());
    }

    @Test
    public void shouldProduceStatesForLocalCache() throws Exception {
        LocalDukascopyCache local = new LocalDukascopyCache(mapper, new DirectDukascopyNoCache());
        DukascopyCache.BarCache localBars = local.createBarCache(validator, tickSearch);

        assertBarsRetrieved(localBars);
        assertThat(localBars.getCacheStatistics().cacheStats()).contains("LocalBarCache", "DirectDukascopyBarNoCache");
    }

    private void assertBarsRetrieved(DukascopyCache.BarCache cache) throws IOException {
        List<Bar> bars = cache.getOneDayOfTicksAsBar(criteria, paths);
        assertThat(bars).isEqualTo(ModelPrototype.loadBars("/bars/BarCacheTestData.json"));
    }
}
