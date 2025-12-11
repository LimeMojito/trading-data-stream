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
import tools.jackson.databind.json.JsonMapper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.limemojito.trading.model.tick.dukascopy.DukascopyUtils.*;
import static java.lang.System.getProperty;

/**
 * A {@link com.limemojito.trading.model.tick.dukascopy.DukascopyCache} implementation that stores
 * downloaded Dukascopy data on the local filesystem. This cache is typically used in combination with
 * another cache or a direct-downloader as a fallback, forming a pipeline where the local cache is
 * consulted first, and a more remote or expensive source is queried only when needed.
 * <p>
 * Default cache directory can be overridden using the system property {@link #PROP_DIR}. If not
 * provided, it defaults to {@code ${user.home}/.dukascopy-cache}.
 * <p>
 * Example pipeline usage:
 * <pre>
 * {@code
 * DukascopyCache cache = new LocalDukascopyCache(
 *         mapper,
 *         new S3DukascopyCache(s3, "myBucket", new DirectDukascopyNoCache(mapper))
 * );
 * }
 * </pre>
 */
@Slf4j
public class LocalDukascopyCache extends FallbackDukascopyCache {
    /**
     * Property for overriding the local cache location.  Defaults to "user.home"/.dukascopy/.
     */
    public static final String PROP_DIR = DirectDukascopyNoCache.class.getPackageName() + ".localCacheDir";

    private static final int TO_KB = 1_024;

    private final JsonMapper mapper;
    private final Path cacheDirectory;

    /**
     * Create a local-cache-first {@code DukascopyCache} that falls back to the supplied cache when a
     * requested object is not present locally. The cache directory is resolved from the system property
     * {@link #PROP_DIR} and, if absent, defaults to {@code ${user.home}/.dukascopy-cache}.
     *
     * @param mapper   Jackson {@link JsonMapper} for serializing/deserializing cached bar collections
     * @param fallback the cache to consult when an item is not present in the local cache
     */
    public LocalDukascopyCache(JsonMapper mapper, DukascopyCache fallback) {
        this(mapper, fallback, new File(getProperty(PROP_DIR, getProperty("user.home")),
                                        ".dukascopy-cache").toPath());
    }

    /**
     * Create a local-cache-first {@code DukascopyCache} using the provided cache directory.
     *
     * @param mapper    Jackson {@link JsonMapper} for serializing/deserializing cached bar collections
     * @param fallback  the cache to consult when an item is not present in the local cache
     * @param directory the root directory on the local filesystem where cache files will be stored
     */
    public LocalDukascopyCache(JsonMapper mapper, DukascopyCache fallback, Path directory) {
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

    /**
     * Recursively deletes all files and directories within the configured local cache directory.
     * The root directory itself is left in place if it already exists.
     *
     * @throws IOException if walking the cache directory fails
     */
    public void removeCache() throws IOException {
        log.warn("Removing cache at {}", cacheDirectory);
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
    protected Optional<InputStream> checkCache(String dukascopyPath) throws IOException {
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
        protected Optional<List<Bar>> checkCache(BarCriteria criteria, String firstDukascopyDayPath) throws
                                                                                                     IOException {
            Optional<InputStream> inputStream = checkLocal(createBarPath(criteria, firstDukascopyDayPath));
            return inputStream.isEmpty() ? Optional.empty() : Optional.of(fromJsonStream(mapper, inputStream.get()));
        }
    }

    private synchronized void saveLocal(String path, InputStream input) throws IOException {
        if (!unsafeIsPresent(path)) {
            Path cachePath = Path.of(cacheDirectory.toString(), path);
            //noinspection ResultOfMethodCallIgnored
            cachePath.toFile().getParentFile().mkdirs();
            Files.copy(input, cachePath);
            log.debug("Saved {} in local cache {} {}KB", path, cachePath, Files.size(cachePath) / TO_KB);
        } else {
            log.warn("Skipped saving {} to local cache as it already exists", path);
        }
    }

    private synchronized Optional<InputStream> checkLocal(String path) throws FileNotFoundException {
        File file = Path.of(cacheDirectory.toString(), path).toFile();
        if (file.isFile()) {
            log.debug("Found in local cache {}", file);
            return Optional.of(new FileInputStream(file));
        } else {
            return Optional.empty();
        }
    }

    private boolean unsafeIsPresent(String path) {
        return Path.of(cacheDirectory.toString(), path).toFile().isFile();
    }

}
