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

import com.limemojito.trading.model.CacheStatistics;
import com.limemojito.trading.model.MarketStatus;
import com.limemojito.trading.model.ModelPrototype;
import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.tick.dukascopy.DukascopyCache;
import com.limemojito.trading.model.tick.dukascopy.DukascopyPathGenerator;
import com.limemojito.trading.model.tick.dukascopy.DukascopyTickSearch;
import com.limemojito.trading.model.tick.dukascopy.DukascopyUtils;
import com.limemojito.trading.model.tick.dukascopy.criteria.BarCriteria;
import jakarta.validation.Validator;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static com.limemojito.trading.model.bar.Bar.Period.M10;
import static com.limemojito.trading.model.tick.dukascopy.DukascopyUtils.setupObjectMapper;
import static com.limemojito.trading.model.tick.dukascopy.DukascopyUtils.setupValidator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class S3DukascopyCacheTest {
    private final String dukascopyTickPath = "EURUSD/2018/06/05/05h_ticks.bi5";
    private final String bucketName = "bucketName";
    private final JsonMapper mapper = setupObjectMapper();
    private final Validator validator = setupValidator();
    private final DukascopyPathGenerator pathGenerator = new DukascopyPathGenerator(new MarketStatus());
    private final BarCriteria criteria = new BarCriteria("EURUSD",
                                                         M10,
                                                         Instant.parse("2020-06-07T04:00:00Z"),
                                                         Instant.parse("2020-06-07T05:00:00Z"));
    private final List<String> paths = pathGenerator.generatePaths(criteria.getSymbol(),
                                                                   criteria.getDayStart(0),
                                                                   criteria.getDayEnd(0));
    @Mock
    private S3Client s3;
    @Mock
    private DukascopyCache fallbackMock;
    @Mock
    private DukascopyTickSearch tickSearch;
    @Mock
    private DukascopyCache.BarCache fallbackBarCache;
    @Captor
    private ArgumentCaptor<Consumer<PutObjectRequest.Builder>> putRequestCaptor;

    private S3DukascopyCache cache;

    @BeforeEach
    void setUp() {
        doReturn(new CacheStatistics.SimpleCacheStatistics("mockCache")).when(fallbackMock).getCacheStatistics();
        cache = new S3DukascopyCache(s3, bucketName, mapper, fallbackMock);
    }

    @AfterEach
    void verifyMocks() {
        verifyNoMoreInteractions(s3, fallbackMock, tickSearch, fallbackBarCache);
    }

    @Test
    public void shouldPullFromS3Ok() throws IOException {
        doReturn(inputToResponse(validDukascopyTickInputStream())).when(s3).getObjectAsBytes(any(Consumer.class));

        try (InputStream stream = cache.stream(dukascopyTickPath)) {
            assertStreamResult(stream, 1, 0);
        }
        assertThat(cache.getCacheStatistics()
                        .cacheStats()).isEqualTo("S3DukascopyCache: retrieve: 1, hit: 1, miss: 0, mockCache: retrieve: 0, hit: 0, miss: 0");
    }

    @Test
    public void shouldFallbackWhenMissingFromS3() throws Exception {
        whenNotInCache();
        whenEmptyCacheOnSave();
        whenDukascopyCalledOk(validDukascopyTickInputStream());
        whenSaveCacheOk();

        try (InputStream stream = cache.stream(dukascopyTickPath)) {
            assertStreamResult(stream, 0, 1);
        }

        verifyS3CacheFetch();
        verifyS3CacheCheck();
        verifyS3CachePut();
        assertPutRequest(putRequestCaptor.getValue());
        assertThat(cache.getCacheStatistics().cacheStats()).isEqualTo("S3DukascopyCache: retrieve: 1, hit: 0, miss: 1, mockCache: retrieve: 0, hit: 0, miss: 0");
    }

    @Test
    public void shouldFetchBarFromS3Ok() throws Exception {
        doReturn(fallbackBarCache).when(fallbackMock).createBarCache(validator, tickSearch);
        doReturn(new CacheStatistics.SimpleCacheStatistics("BarMockStats")).when(fallbackBarCache).getCacheStatistics();
        DukascopyCache.BarCache barCache = cache.createBarCache(validator, tickSearch);
        final InputStream inputStream = validBarListInputStream();
        doReturn(inputToResponse(inputStream)).when(s3).getObjectAsBytes(any(Consumer.class));

        List<Bar> bar = barCache.getOneDayOfTicksAsBar(criteria, paths);

        assertThat(bar.size()).isGreaterThan(0);
        verify(fallbackMock).createBarCache(validator, tickSearch);
        verifyS3CacheFetch();
        assertThat(barCache.getCacheStatistics().getHitCount()).isEqualTo(1);
        assertThat(barCache.getCacheStatistics().getMissCount()).isEqualTo(0);
        assertThat(barCache.getCacheStatistics().getRetrieveCount()).isEqualTo(1);
    }

    @Test
    public void shouldSaveBarToS3Ok() throws Exception {
        doReturn(fallbackBarCache).when(fallbackMock).createBarCache(validator, tickSearch);
        doReturn(new CacheStatistics.SimpleCacheStatistics("BarMockStats")).when(fallbackBarCache).getCacheStatistics();

        DukascopyCache.BarCache barCache = cache.createBarCache(validator, tickSearch);
        // is there a bar result in S3?
        whenNotInCache();
        // is there a bar result when saving in S3?
        whenEmptyCacheOnSave();
        List<Bar> expected = ModelPrototype.loadBars("/bars/BarCacheTestData.json");
        doReturn(expected).when(fallbackBarCache).getOneDayOfTicksAsBar(criteria, paths);
        whenSaveCacheOk();

        List<Bar> bar = barCache.getOneDayOfTicksAsBar(criteria, paths);

        assertThat(bar.size()).isGreaterThan(0);
        verify(fallbackMock).createBarCache(validator, tickSearch);
        verifyS3CacheFetch();
        verifyS3CacheCheck();
        verify(fallbackBarCache).getOneDayOfTicksAsBar(criteria, paths);
        verifyS3CachePut();
        verifyBarPutObjectRequest(putRequestCaptor.getValue(), barCache);
    }

    private void verifyS3CachePut() {
        verify(s3).putObject(eq(putRequestCaptor.getValue()), any(RequestBody.class));
    }


    private void verifyS3CacheCheck() {
        verify(s3).headObject(any(Consumer.class));
    }

    private void verifyS3CacheFetch() {
        verify(s3).getObjectAsBytes(any(Consumer.class));
    }

    private void whenEmptyCacheOnSave() {
        doThrow(NoSuchKeyException.class).when(s3).headObject(any(Consumer.class));
    }

    private void whenNotInCache() {
        doThrow(NoSuchKeyException.class).when(s3).getObjectAsBytes(any(Consumer.class));
    }

    @SuppressWarnings("resource")
    private void whenDukascopyCalledOk(InputStream input) throws IOException {
        doReturn(input).when(fallbackMock).stream(dukascopyTickPath);
    }

    private void whenSaveCacheOk() {
        doReturn(PutObjectResponse.builder().build()).when(s3)
                                                     .putObject(putRequestCaptor.capture(), any(RequestBody.class));
    }

    private void verifyBarPutObjectRequest(Consumer<PutObjectRequest.Builder> consumer,
                                           DukascopyCache.BarCache barCache) {
        PutObjectRequest.Builder build = PutObjectRequest.builder();
        consumer.accept(build);
        PutObjectRequest request = build.build();
        assertThat(request.bucket()).isEqualTo(bucketName);
        assertThat(request.key()).startsWith("bars/M10/EURUSD/2020/05/07.json");
        assertThat(request.contentType()).isEqualTo("application/json");
        assertThat(barCache.getCacheStatistics().getHitCount()).isEqualTo(0);
        assertThat(barCache.getCacheStatistics().getMissCount()).isEqualTo(1);
        assertThat(barCache.getCacheStatistics().getRetrieveCount()).isEqualTo(1);
    }

    private void assertPutRequest(Consumer<PutObjectRequest.Builder> consumer) {
        PutObjectRequest.Builder build = PutObjectRequest.builder();
        consumer.accept(build);
        PutObjectRequest request = build.build();
        assertThat(request.bucket()).isEqualTo(bucketName);
        assertThat(request.key()).isEqualTo(dukascopyTickPath);
        assertThat(request.contentLength()).isGreaterThan(33000L);
        assertThat(request.contentType()).isEqualTo("application/octet-stream");
        assertThat(request.contentDisposition()).isEqualTo(dukascopyTickPath);
    }

    private void assertStreamResult(InputStream stream, int hits, int misses) {
        assertThat(stream).isNotNull();
        assertThat(cache.getCacheStatistics().getHitCount()).isEqualTo(hits);
        assertThat(cache.getCacheStatistics().getMissCount()).isEqualTo(misses);
        assertThat(cache.getCacheStatistics().getRetrieveCount()).isEqualTo(hits + misses);
    }

    private static @NonNull ResponseBytes<GetObjectResponse> inputToResponse(InputStream inputStream) throws
                                                                                                      IOException {
        byte[] data = IOUtils.toByteArray(inputStream);
        return ResponseBytes.fromByteArray(GetObjectResponse.builder()
                                                            .contentLength((long) data.length)
                                                            .build(),
                                           data);
    }

    private InputStream validBarListInputStream() {
        return ModelPrototype.loadStream("/bars/BarCacheTestData.json");
    }

    private InputStream validDukascopyTickInputStream() throws IOException {
        return new FileInputStream(DukascopyUtils.dukascopyClassResourceToTempFile("/" + dukascopyTickPath));
    }
}
