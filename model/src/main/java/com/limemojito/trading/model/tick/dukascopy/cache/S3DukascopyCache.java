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

import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.tick.dukascopy.DukascopyCache;
import com.limemojito.trading.model.tick.dukascopy.DukascopyTickSearch;
import com.limemojito.trading.model.tick.dukascopy.criteria.BarCriteria;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static com.limemojito.trading.model.tick.dukascopy.DukascopyUtils.*;

/**
 * A {@link com.limemojito.trading.model.tick.dukascopy.DukascopyCache} implementation that stores and
 * retrieves data from Amazon S3, optionally delegating to a fallback cache when objects are missing.
 * The service can cache both raw Dukascopy binary files and pre-aggregated bar JSON files.
 */
@Service
@Slf4j
public class S3DukascopyCache extends FallbackDukascopyCache {

    private static final int TO_KB = 1_024;
    private final S3Client s3;
    private final String bucketName;
    private final JsonMapper mapper;

    /**
     * Create an S3-backed Dukascopy cache with a fallback cache.
     *
     * @param s3         Amazon S3 client used to store and retrieve objects
     * @param bucketName target S3 bucket
     * @param mapper     Jackson mapper for JSON bar payloads
     * @param fallback   cache to consult when an object is not present in S3
     */
    public S3DukascopyCache(S3Client s3, String bucketName, JsonMapper mapper, DukascopyCache fallback) {
        super(fallback);
        this.s3 = s3;
        this.bucketName = bucketName;
        this.mapper = mapper;
    }

    /**
     * Creates a bar cache that first checks and stores data in S3 and delegates to the fallback cache on miss.
     *
     * @param validator  bean validator for bar data
     * @param tickSearch provider capable of locating Dukascopy bar files
     * @return a composed bar cache backed by S3
     */
    @Override
    public BarCache createBarCache(Validator validator, DukascopyTickSearch tickSearch) {
        return new S3BarCache(getFallback().createBarCache(validator, tickSearch));
    }

    @Override
    protected void saveToCache(String dukascopyPath, InputStream input) throws IOException {
        saveToS3(dukascopyPath, input, "application/octet-stream");
    }

    @Override
    protected Optional<InputStream> checkCache(String dukascopyPath) {
        return checkS3(dukascopyPath);
    }

    private final class S3BarCache extends FallbackBarCache {
        private S3BarCache(BarCache fallback) {
            super(fallback);
        }

        @Override
        protected void saveToCache(BarCriteria criteria,
                                   String dukascopyPath,
                                   List<Bar> oneDayOfBars) throws IOException {
            saveToS3(createBarPath(criteria, dukascopyPath), toJsonStream(mapper, oneDayOfBars), "application/json");
        }

        @Override
        protected Optional<List<Bar>> checkCache(BarCriteria criteria, String firstDukascopyDayPath) throws IOException {
            final Optional<InputStream> found = checkS3(createBarPath(criteria, firstDukascopyDayPath));
            return found.isEmpty() ? Optional.empty() : Optional.of(fromJsonStream(mapper, found.get()));
        }
    }

    private synchronized void saveToS3(String path, InputStream input, String contentType) throws IOException {
        if (!unsafeIsPresent(path)) {
            final byte[] bytes = IOUtils.toByteArray(input);
            try (ByteArrayInputStream s3Input = new ByteArrayInputStream(bytes)) {
                log.info("Saving to s3://{}/{} size {} KB", bucketName, path, bytes.length / TO_KB);
                RequestBody body = RequestBody.fromInputStream(s3Input, bytes.length);
                s3.putObject(b -> b.bucket(bucketName)
                                   .key(path)
                                   .contentType(contentType)
                                   .contentDisposition(path)
                                   .contentLength((long) bytes.length),
                             body);
            }
        }
    }

    private synchronized Optional<InputStream> checkS3(String path) {
        try {
            final InputStream inputStream = s3.getObjectAsBytes(b -> b.bucket(bucketName).key(path)).asInputStream();
            log.info("Retrieving s3://{}/{}", bucketName, path);
            return Optional.of(inputStream);
        } catch (NoSuchKeyException e) {
            log.debug("{} is not in S3", path);
            return Optional.empty();
        }
    }

    private boolean unsafeIsPresent(String path) {
        try {
            s3.headObject(b -> b.bucket(bucketName).key(path));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}

