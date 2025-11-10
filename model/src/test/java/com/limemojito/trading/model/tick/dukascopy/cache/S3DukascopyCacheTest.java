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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limemojito.trading.model.MarketStatus;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.Validator;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import static com.limemojito.trading.model.bar.Bar.Period.M10;
import static com.limemojito.trading.model.tick.dukascopy.DukascopyUtils.setupObjectMapper;
import static com.limemojito.trading.model.tick.dukascopy.DukascopyUtils.setupValidator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class S3DukascopyCacheTest {
    private final String dukascopyTickPath = "EURUSD/2018/06/05/05h_ticks.bi5";
    private final String bucketName = "bucketName";
    private final ObjectMapper mapper = setupObjectMapper();
    private final Validator validator = setupValidator();
    private final DukascopyPathGenerator pathGenerator = new DukascopyPathGenerator(new MarketStatus());
    private final BarCriteria criteria = new BarCriteria("EURUSD",
                                                         M10,
                                                         Instant.parse("2019-06-07T04:00:00Z"),
                                                         Instant.parse("2019-06-07T05:00:00Z"));
    private final List<String> paths = pathGenerator.generatePaths(criteria.getSymbol(),
                                                                   criteria.getDayStart(0),
                                                                   criteria.getDayEnd(0));
    @Mock
    private AmazonS3 s3;
    @Mock
    private DukascopyCache fallbackMock;
    @Mock
    private DukascopyTickSearch tickSearch;
    @Mock
    private DukascopyCache.BarCache fallbackBarCache;
    @Captor
    private ArgumentCaptor<PutObjectRequest> putRequestCaptor;
    private S3DukascopyCache cache;

    @BeforeEach
    void setUp() {
        cache = new S3DukascopyCache(s3, bucketName, mapper, fallbackMock);
    }

    @AfterEach
    void verifyMocks() {
        verifyNoMoreInteractions(s3, fallbackMock, tickSearch, fallbackBarCache);
    }

    @Test
    public void shouldPullFromS3Ok() throws IOException {
        doReturn(true).when(s3).doesObjectExist(bucketName, dukascopyTickPath);
        doReturn(validTickObject()).when(s3).getObject(bucketName, dukascopyTickPath);
        doReturn("mockCache").when(fallbackMock).cacheStats();

        try (InputStream stream = cache.stream(dukascopyTickPath)) {
            assertStreamResult(stream, 1, 0);
        }
        assertThat(cache.cacheStats()).isEqualTo("S3DukascopyCache 1 1h 0m 100.00% -> (mockCache)");
    }

    @Test
    @SuppressWarnings("resource")
    public void shouldFallbackWhenMissingFromS3() throws Exception {
        doReturn(false).when(s3).doesObjectExist(bucketName, dukascopyTickPath);
        doReturn(validInputStream()).when(fallbackMock).stream(dukascopyTickPath);
        doReturn(new PutObjectResult()).when(s3).putObject(putRequestCaptor.capture());
        doReturn("mockCache").when(fallbackMock).cacheStats();

        try (InputStream stream = cache.stream(dukascopyTickPath)) {
            assertStreamResult(stream, 0, 1);
        }
        verify(s3).putObject(putRequestCaptor.getValue());
        assertPutRequest(putRequestCaptor.getValue());
        assertThat(cache.cacheStats()).isEqualTo("S3DukascopyCache 1 0h 1m 0.00% -> (mockCache)");
    }

    @Test
    public void shouldFetchBarFromS3Ok() throws Exception {
        doReturn(fallbackBarCache).when(fallbackMock).createBarCache(validator, tickSearch);

        DukascopyCache.BarCache barCache = cache.createBarCache(validator, tickSearch);
        doReturn(true).when(s3).doesObjectExist(eq(bucketName), anyString());
        doReturn(validBarListObject()).when(s3).getObject(eq(bucketName), anyString());

        List<Bar> bar = barCache.getOneDayOfTicksAsBar(criteria, paths);

        assertThat(bar.size()).isGreaterThan(0);
        verify(fallbackMock).createBarCache(validator, tickSearch);
        verify(s3).doesObjectExist(eq(bucketName), anyString());
        verify(s3).getObject(eq(bucketName), anyString());
        assertThat(barCache.getHitCount()).isEqualTo(1);
        assertThat(barCache.getMissCount()).isEqualTo(0);
        assertThat(barCache.getRetrieveCount()).isEqualTo(1);
    }

    @Test
    public void shouldSaveBarToS3Ok() throws Exception {
        doReturn(fallbackBarCache).when(fallbackMock).createBarCache(validator, tickSearch);

        DukascopyCache.BarCache barCache = cache.createBarCache(validator, tickSearch);
        doReturn(false).when(s3).doesObjectExist(eq(bucketName), anyString());
        List<Bar> expected = ModelPrototype.loadBars("/bars/BarCacheTestData.json");
        doReturn(expected).when(fallbackBarCache).getOneDayOfTicksAsBar(criteria, paths);
        doReturn(new PutObjectResult()).when(s3).putObject(putRequestCaptor.capture());

        List<Bar> bar = barCache.getOneDayOfTicksAsBar(criteria, paths);

        assertThat(bar.size()).isGreaterThan(0);
        verify(fallbackMock).createBarCache(validator, tickSearch);
        // twice as we check around sync lock.
        verify(s3, times(2)).doesObjectExist(eq(bucketName), anyString());
        verify(fallbackBarCache).getOneDayOfTicksAsBar(criteria, paths);
        PutObjectRequest request = putRequestCaptor.getValue();
        assertThat(request.getBucketName()).isEqualTo(bucketName);
        assertThat(request.getKey()).startsWith("bars/M10/EURUSD/2019/05/07.json");
        assertThat(request.getMetadata().getContentType()).isEqualTo("application/json");
        verify(s3).putObject(request);
        assertThat(barCache.getHitCount()).isEqualTo(0);
        assertThat(barCache.getMissCount()).isEqualTo(1);
        assertThat(barCache.getRetrieveCount()).isEqualTo(1);
    }

    private void assertPutRequest(PutObjectRequest request) {
        assertThat(request.getBucketName()).isEqualTo(bucketName);
        assertThat(request.getKey()).isEqualTo(dukascopyTickPath);
        assertThat(request.getMetadata().getContentLength()).isGreaterThan(33000L);
        assertThat(request.getMetadata().getContentType()).isEqualTo("application/octet-stream");
        assertThat(request.getMetadata().getContentDisposition()).isEqualTo(dukascopyTickPath);
    }

    private void assertStreamResult(InputStream stream, int hits, int misses) {
        assertThat(stream).isNotNull();
        assertThat(cache.getHitCount()).isEqualTo(hits);
        assertThat(cache.getMissCount()).isEqualTo(misses);
        assertThat(cache.getRetrieveCount()).isEqualTo(hits + misses);
    }

    private S3Object validTickObject() throws IOException {
        S3Object s3Object = new S3Object();
        s3Object.setObjectContent(validInputStream());
        return s3Object;
    }

    private S3Object validBarListObject() {
        S3Object s3Object = new S3Object();
        s3Object.setObjectContent(validBarListInputStream());
        return s3Object;
    }

    private InputStream validBarListInputStream() {
        return ModelPrototype.loadStream("/bars/BarCacheTestData.json");
    }

    private InputStream validInputStream() throws IOException {
        return new FileInputStream(DukascopyUtils.dukascopyClassResourceToTempFile("/" + dukascopyTickPath));
    }
}
