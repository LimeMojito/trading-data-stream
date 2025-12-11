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

import com.limemojito.trading.model.tick.dukascopy.DukascopyCache;
import com.limemojito.trading.model.tick.dukascopy.DukascopyPathGenerator;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Prime a cache by preloading data using the supplied strategy.
 */
@Slf4j
public class DukascopyCachePrimer {
    private final DukascopyCache cache;
    private final DukascopyPathGenerator pathGenerator;
    private final ExecutorService executor;
    private final List<Future<?>> results;

    /**
     * Prime a cache using the available CPU cores.
     *
     * @param cache         Cache strategy to prime.
     * @param pathGenerator Dukascopy path generator to use for data retrieval.
     */
    public DukascopyCachePrimer(DukascopyCache cache, DukascopyPathGenerator pathGenerator) {
        this.cache = cache;
        this.pathGenerator = pathGenerator;
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.results = new ArrayList<>();
    }

    /**
     * Clear any pending loads and start a new load.
     */
    public void newLoad() {
        results.clear();
    }

    /**
     * Load data for the specified symbol and time range.
     *
     * @param symbol Symbol to load data for.
     * @param start  Start time for data retrieval.
     * @param end    End time for data retrieval.
     */
    public void load(String symbol, Instant start, Instant end) {
        List<String> paths = this.pathGenerator.generatePaths(symbol, start, end);
        for (String path : paths) {
            Future<?> submit = executor.submit(() -> {
                try (InputStream dataStream = cache.stream(path)) {
                    // s3 complains LOUDLY if we don't use the data.
                    byte[] bytes = IOUtils.toByteArray(dataStream);
                    log.info("Loaded {} {}b", path, bytes.length);
                } catch (IOException e) {
                    log.error("Failed to load {} {}", path, e.getMessage(), e);
                }
            });
            results.add(submit);
        }
    }

    /**
     * Wait for the async load to complete.
     */
    @SneakyThrows
    public void waitForCompletion() {
        log.info("Waiting for completion.");
        for (Future<?> result : results) {
            result.get();
        }
    }

    /**
     * Close and shutdown executors.
     */
    public void shutdown() {
        executor.shutdownNow();
    }
}
