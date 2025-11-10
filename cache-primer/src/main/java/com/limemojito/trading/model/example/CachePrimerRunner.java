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

package com.limemojito.trading.model.example;

import com.limemojito.trading.model.tick.dukascopy.DukascopyCache;
import com.limemojito.trading.model.tick.dukascopy.cache.DukascopyCachePrimer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CachePrimerRunner implements ApplicationRunner {

    private final DukascopyCache cache;
    private final DukascopyCachePrimer cacheLoader;

    @Override
    public void run(ApplicationArguments args) {
        List<String> symbols = getRequiredValues(args, "symbol");
        Instant start = getRequiredValueInstant(args, "start");
        Instant end = getRequiredValueInstant(args, "end");
        log.info("Performing cache priming {} {} -> {}", symbols, start, end);
        cacheLoader.newLoad();
        for (String symbol : symbols) {
            cacheLoader.load(symbol, start, end);
        }
        cacheLoader.waitForCompletion();
        log.info(cache.cacheStats());
        cacheLoader.shutdown();
    }

    private Instant getRequiredValueInstant(ApplicationArguments args, String name) {
        return Instant.parse(getRequiredValue(args, name));
    }

    private static String getRequiredValue(ApplicationArguments args, String name) {
        List<String> optionValues = getRequiredValues(args, name);
        return optionValues.get(0);
    }

    private static List<String> getRequiredValues(ApplicationArguments args, String name) {
        if (!args.containsOption(name)) {
            throw new IllegalArgumentException("Missing command line argument --" + name + "=????");
        }
        return args.getOptionValues(name);
    }
}
