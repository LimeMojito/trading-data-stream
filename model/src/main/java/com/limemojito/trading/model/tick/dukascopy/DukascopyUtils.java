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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.tick.TickInputStreamToCsv;
import com.limemojito.trading.model.tick.dukascopy.cache.DirectDukascopyNoCache;
import com.limemojito.trading.model.tick.dukascopy.cache.LocalDukascopyCache;
import com.limemojito.trading.model.tick.dukascopy.criteria.BarCriteria;
import jakarta.validation.Configuration;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

import static java.lang.String.format;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Slf4j
public class DukascopyUtils {

    private static final TypeReference<List<Bar>> BAR_TYPE = new TypeReference<>() {
    };
    private static DukascopySearch lazySearch;
    private static Validator lazyValidator;
    private static ObjectMapper lazyMapper;

    /**
     * A basic search configuration with local cache suitable for testing only.  Default validator
     * and object mapper implementations.  You should favour injecting default spring boot starters rather
     * than calling this method.
     *
     * @return A configured search.
     */
    public static DukascopySearch standaloneSetup() {
        return standaloneSetup(setupValidator(), setupObjectMapper());
    }

    /**
     * A basic search configuration with local cache.
     *
     * @param validator Validation api.
     * @param mapper    Jackson JSON api.
     * @return A configured search.
     */
    public static DukascopySearch standaloneSetup(Validator validator, ObjectMapper mapper) {
        if (lazySearch == null) {
            final DukascopyCache cacheChain = new LocalDukascopyCache(mapper, new DirectDukascopyNoCache());
            final DukascopyPathGenerator pathGenerator = new DukascopyPathGenerator();
            lazySearch = new DukascopySearch(validator, cacheChain, pathGenerator);
            log.info("Standalone setup with cache chain {}", cacheChain.getClass().getSimpleName());
        }
        return lazySearch;
    }

    /**
     * Support class for quick and dirty command lines that are not spring containers.
     * Favour injecting the spring supplied one.
     *
     * @return A basic validator minimal configuration.
     */
    public static Validator setupValidator() {
        if (lazyValidator == null) {
            Configuration<?> config = Validation.byDefaultProvider().configure();
            ValidatorFactory factory = config.buildValidatorFactory();
            lazyValidator = factory.getValidator();
            factory.close();
            log.info("Configured Validator");
        }
        return lazyValidator;
    }

    /**
     * Support class for quick and dirty command lines that are not spring containers.
     * Favour injecting the spring supplied one.
     *
     * @return A basic validator minimal configuration.
     */
    public static ObjectMapper setupObjectMapper() {
        if (lazyMapper == null) {
            // register models to get java time support, etc. if on command line/
            lazyMapper = new ObjectMapper().findAndRegisterModules();
            log.info("Configured Jackson Object Mapper");
        }
        return lazyMapper;
    }

    /**
     * Copies the path of the class resource to an appropriately configured temp path
     *
     * @param path Path of dukascopy resource assumed to be from root of classpath.
     * @return The location of the temp copy.
     * @throws IOException on an io failure.
     */
    public static File dukascopyClassResourceToTempFile(String path) throws IOException {
        final String tempPath = System.getProperty("java.io.tmpdir");
        final File outputPath = new File(tempPath, path);
        log.info("Copying {} to {}", "classpath:" + path, outputPath.getAbsolutePath());
        //noinspection ResultOfMethodCallIgnored
        outputPath.mkdirs();
        try (InputStream resourceAsStream = TickInputStreamToCsv.class.getResourceAsStream(path)) {
            if (resourceAsStream == null) {
                throw new IOException(format("Could not open classpath resource %s", path));
            }
            long size = Files.copy(resourceAsStream, outputPath.toPath(), REPLACE_EXISTING);
            log.debug("Copied {} bytes", size);
        }
        return outputPath;
    }

    public static InputStream toJsonStream(ObjectMapper mapper, List<Bar> bars) throws IOException {
        return new ByteArrayInputStream(mapper.writeValueAsBytes(bars));
    }

    public static List<Bar> fromJsonStream(ObjectMapper mapper, InputStream inputStream) throws IOException {
        return mapper.readValue(inputStream, BAR_TYPE);
    }

    public static String createBarPath(BarCriteria barCriteria, String firstDukascopyDayPath) {
        String datePart = firstDukascopyDayPath.substring(firstDukascopyDayPath.indexOf('/') + 1,
                                                          firstDukascopyDayPath.lastIndexOf('/'));
        String barPath = format("bars/%s/%s/%s.json", barCriteria.getPeriod(), barCriteria.getSymbol(), datePart);
        log.debug("Bar path {} from {} {}", barPath, barCriteria, firstDukascopyDayPath);
        return barPath;
    }
}
