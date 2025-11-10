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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.tick.dukascopy.DukascopyCache;
import com.limemojito.trading.model.tick.dukascopy.DukascopyTickSearch;
import com.limemojito.trading.model.tick.dukascopy.criteria.BarCriteria;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;

import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.limemojito.trading.model.tick.dukascopy.DukascopyUtils.createBarPath;
import static com.limemojito.trading.model.tick.dukascopy.DukascopyUtils.fromJsonStream;
import static com.limemojito.trading.model.tick.dukascopy.DukascopyUtils.toJsonStream;

/**
 * s3, then another cache.
 * Marked as a Service for Spring usage.  Note that properties of support classes can be set as spring properties.
 */
@Service
@Slf4j
public class S3DukascopyCache extends FallbackDukascopyCache {

    private static final int TO_KB = 1_024;
    private final AmazonS3 s3;
    private final String bucketName;
    private final ObjectMapper mapper;

    public S3DukascopyCache(AmazonS3 s3, String bucketName, ObjectMapper mapper, DukascopyCache fallback) {
        super(fallback);
        this.s3 = s3;
        this.bucketName = bucketName;
        this.mapper = mapper;
    }

    @Override
    public BarCache createBarCache(Validator validator, DukascopyTickSearch tickSearch) {
        return new S3BarCache(getFallback().createBarCache(validator, tickSearch));
    }

    @Override
    protected void saveToCache(String dukascopyPath, InputStream input) throws IOException {
        saveToS3(dukascopyPath, input, "application/octet-stream");
    }

    @Override
    protected InputStream checkCache(String dukascopyPath) {
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
        protected List<Bar> checkCache(BarCriteria criteria, String firstDukascopyDayPath) throws IOException {
            S3ObjectInputStream inputStream = checkS3(createBarPath(criteria, firstDukascopyDayPath));
            return inputStream == null ? null : fromJsonStream(mapper, inputStream);
        }

    }

    private synchronized void saveToS3(String path, InputStream input, String contentType) throws IOException {
        if (!unsafeIsPresent(path)) {
            final byte[] bytes = IOUtils.toByteArray(input);
            final ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType(contentType);
            metadata.setContentDisposition(path);
            try (ByteArrayInputStream s3Input = new ByteArrayInputStream(bytes)) {
                log.info("Saving to s3://{}/{} size {} KB", bucketName, path, bytes.length / TO_KB);
                s3.putObject(new PutObjectRequest(bucketName, path, s3Input, metadata));
            }
        }
    }

    private synchronized S3ObjectInputStream checkS3(String path) {
        if (unsafeIsPresent(path)) {
            log.info("Retrieving s3://{}/{}", bucketName, path);
            return s3.getObject(bucketName, path).getObjectContent();
        } else {
            return null;
        }
    }

    private boolean unsafeIsPresent(String path) {
        return s3.doesObjectExist(bucketName, path);
    }
}

