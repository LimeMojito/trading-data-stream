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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.tick.dukascopy.DukascopyCache;
import com.limemojito.trading.model.tick.dukascopy.DukascopyTickSearch;
import com.limemojito.trading.model.tick.dukascopy.criteria.BarCriteria;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.limemojito.trading.model.tick.dukascopy.DukascopyUtils.createBarPath;
import static com.limemojito.trading.model.tick.dukascopy.DukascopyUtils.fromJsonStream;
import static com.limemojito.trading.model.tick.dukascopy.DukascopyUtils.toJsonStream;
import static java.lang.System.getProperty;

/**
 * This can be mixed with other cache strategies to pipeline  For example:
 * <p>
 * <code>DukascopyCache cache = new LocalDukascopyCache(new S3DukascopyCache(s3, "myBucket", new NoCacheDirectDukascopy()))</code>
 */
@Slf4j
public class LocalDukascopyCache extends FallbackDukascopyCache {
    /**
     * Property for overriding local cache location.  Defaults to "user.home"/.dukascopy/.
     */
    public static final String PROP_DIR = DirectDukascopyNoCache.class.getPackageName() + ".localCacheDir";
    private static final int TO_KB = 1_024;

    private final ObjectMapper mapper;
    private final Path cacheDirectory;

    public LocalDukascopyCache(ObjectMapper mapper, DukascopyCache fallback) {
        this(mapper, fallback, new File(getProperty(PROP_DIR, getProperty("user.home")),
                                        ".dukascopy-cache").toPath());
    }

    public LocalDukascopyCache(ObjectMapper mapper, DukascopyCache fallback, Path directory) {
        super(fallback);
        this.mapper = mapper;
        if (directory.toFile().mkdir()) {
            log.info("Created local cache at {}", directory);
        }
        this.cacheDirectory = directory;
    }

    /**
     * Compute the total size, in bytes, of files currently stored in the local cache directory.
     *
     * @return total cache size in bytes
     * @throws IOException if walking the cache directory fails
     */
    public long getCacheSizeBytes() throws IOException {
        try (Stream<Path> walk = Files.walk(cacheDirectory)) {
            final Optional<Long> size = walk.map(Path::toFile)
                                            .map(File::length)
                                            .reduce(Long::sum);
            return size.orElse(0L);
        }
    }

    public void removeCache() throws IOException {
        log.info("Removing cache at {}", cacheDirectory);
        try (Stream<Path> walk = Files.walk(cacheDirectory)) {
            //noinspection ResultOfMethodCallIgnored
            walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    @Override
    public BarCache createBarCache(Validator validator, DukascopyTickSearch tickSearch) {
        return new LocalBarCache(getFallback().createBarCache(validator, tickSearch));
    }

    @Override
    protected void saveToCache(String dukascopyPath, InputStream input) throws IOException {
        saveLocal(dukascopyPath, input);
    }

    @Override
    protected InputStream checkCache(String dukascopyPath) throws IOException {
        return checkLocal(dukascopyPath);
    }

    private final class LocalBarCache extends FallbackBarCache {
        private LocalBarCache(BarCache fallbackBarCache) {
            super(fallbackBarCache);
        }

        @Override
        protected void saveToCache(BarCriteria criteria,
                                   String firstDukascopyDayPath,
                                   List<Bar> oneDayOfBars) throws IOException {
            saveLocal(createBarPath(criteria, firstDukascopyDayPath), toJsonStream(mapper, oneDayOfBars));
        }

        @Override
        protected List<Bar> checkCache(BarCriteria criteria, String firstDukascopyDayPath) throws IOException {
            InputStream inputStream = checkLocal(createBarPath(criteria, firstDukascopyDayPath));
            return inputStream == null ? null : fromJsonStream(mapper, inputStream);
        }
    }

    private synchronized void saveLocal(String path, InputStream input) throws IOException {
        if (!unsafeIsPresent(path)) {
            Path cachePath = Path.of(cacheDirectory.toString(), path);
            //noinspection ResultOfMethodCallIgnored
            cachePath.toFile().getParentFile().mkdirs();
            log.debug("Saving {} to local cache {}", path, cachePath);
            Files.copy(input, cachePath);
            log.debug("Saved {} in local cache {} {}KB", path, cachePath, Files.size(cachePath) / TO_KB);
        } else {
            log.warn("Skipped saving {} to local cache as it already exists", path);
        }
    }

    private synchronized InputStream checkLocal(String path) throws FileNotFoundException {
        boolean present = unsafeIsPresent(path);
        if (present) {
            File file = Path.of(cacheDirectory.toString(), path).toFile();
            log.debug("Found in local cache {}", file);
            return new FileInputStream(file);
        }
        return null;
    }

    private boolean unsafeIsPresent(String path) {
        return Path.of(cacheDirectory.toString(), path).toFile().isFile();
    }

}
