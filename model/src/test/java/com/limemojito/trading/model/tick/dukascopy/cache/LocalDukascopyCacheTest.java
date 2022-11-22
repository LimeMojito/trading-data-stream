/*
 * Copyright 2011-2022 Lime Mojito Pty Ltd
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.limemojito.trading.model.ModelPrototype;
import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.tick.dukascopy.DukascopyCache;
import com.limemojito.trading.model.tick.dukascopy.DukascopyPathGenerator;
import com.limemojito.trading.model.tick.dukascopy.DukascopyTickSearch;
import com.limemojito.trading.model.tick.dukascopy.DukascopyUtils;
import com.limemojito.trading.model.tick.dukascopy.criteria.BarCriteria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.validation.Validator;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static com.limemojito.trading.model.bar.Bar.Period.M10;
import static com.limemojito.trading.model.tick.dukascopy.DukascopyUtils.setupObjectMapper;
import static com.limemojito.trading.model.tick.dukascopy.DukascopyUtils.setupValidator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("resource")
public class LocalDukascopyCacheTest {
    private final String dukascopyTickPath = "EURUSD/2018/06/05/05h_ticks.bi5";

    @Mock
    private DukascopyCache fallbackMock;
    @Mock
    private DukascopyCache.BarCache fallbackBarMock;
    @Mock
    private DukascopyTickSearch tickSearchMock;
    private LocalDukascopyCache cache;
    private final ObjectMapper mapper = setupObjectMapper();
    private final Validator validator = setupValidator();
    private final BarCriteria criteria = new BarCriteria("EURUSD",
                                                         M10,
                                                         Instant.parse("2019-06-07T04:00:00Z"),
                                                         Instant.parse("2019-06-07T05:00:00Z"));
    private final DukascopyPathGenerator pathGenerator = new DukascopyPathGenerator();
    private final List<String> paths = pathGenerator.generatePaths(criteria.getSymbol(),
                                                                   criteria.getDayStart(0),
                                                                   criteria.getDayEnd(0));

    @BeforeEach
    void setUp() throws IOException {
        Path tempDirectory = Files.createTempDirectory("cache-test");
        cache = new LocalDukascopyCache(mapper, fallbackMock, tempDirectory);
    }

    @AfterEach
    void cleanUp() throws IOException {
        assertThat(cache.getCacheSizeBytes()).isGreaterThan(20000L);
        // removing the one setup by temp directory in setup.
        cache.removeCache();
        verifyNoMoreInteractions(fallbackMock, fallbackBarMock, tickSearchMock);
    }

    @Test
    public void shouldPullFromFallbackWhenMissingFromLocalCache() throws IOException {
        doReturn(validInputStream()).when(fallbackMock).stream(dukascopyTickPath);
        doReturn("mock cache").when(fallbackMock).cacheStats();

        try (InputStream stream = cache.stream(dukascopyTickPath)) {
            assertStreamResult(stream, 0, 1);
        }

        assertThat(cache.cacheStats()).isEqualTo("LocalDukascopyCache 1 0h 1m 0.00% -> (mock cache)");
    }

    @Test
    public void shouldHitCacheWhenAlreadyRetrieved() throws Exception {
        doReturn(validInputStream()).when(fallbackMock).stream(dukascopyTickPath);
        InputStream stream = cache.stream(dukascopyTickPath);
        stream.close();


        try (InputStream stream2 = cache.stream(dukascopyTickPath)) {
            assertStreamResult(stream2, 1, 1);
        }
    }

    @Test
    public void shouldSaveBarToLocalCache() throws Exception {
        doReturn(fallbackBarMock).when(fallbackMock).createBarCache(validator, tickSearchMock);
        List<Bar> expected = ModelPrototype.loadBars("/bars/BarCacheTestData.json");
        doReturn(expected).when(fallbackBarMock).getOneDayOfTicksAsBar(criteria, paths);

        DukascopyCache.BarCache barCache = cache.createBarCache(validator, tickSearchMock);
        assertThat(barCache.getRetrieveCount()).isEqualTo(0);

        List<Bar> bars = barCache.getOneDayOfTicksAsBar(criteria, paths);
        assertThat(bars).isEqualTo(expected);
        assertThat(barCache.getRetrieveCount()).isEqualTo(1);
        assertThat(barCache.getMissCount()).isEqualTo(1);

        verify(fallbackBarMock).getOneDayOfTicksAsBar(criteria, paths);
    }

    private void assertStreamResult(InputStream stream, int hits, int misses) {
        assertThat(stream).isNotNull();
        assertThat(cache.getHitCount()).isEqualTo(hits);
        assertThat(cache.getMissCount()).isEqualTo(misses);
        assertThat(cache.getRetrieveCount()).isEqualTo(hits + misses);
    }

    private InputStream validInputStream() throws IOException {
        return new FileInputStream(DukascopyUtils.dukascopyClassResourceToTempFile("/" + dukascopyTickPath));
    }
}
