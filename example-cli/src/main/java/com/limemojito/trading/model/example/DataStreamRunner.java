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

import com.limemojito.trading.model.TradingInputStream;
import com.limemojito.trading.model.TradingSearch;
import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.bar.Bar.Period;
import com.limemojito.trading.model.bar.BarInputStreamToCsv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataStreamRunner implements ApplicationRunner {

    private final TradingSearch tradingSearch;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String symbol = getRequiredValue(args, "symbol");
        Period period = Period.valueOf(getRequiredValue(args, "period"));
        Instant start = getRequiredValueInstant(args, "start");
        Instant end = getRequiredValueInstant(args, "end");
        File outputFile = new File(getRequiredValue(args, "output"));
        log.info("Performing bar aggregation to CSV on {} {} {} -> {} => {}", symbol, period, start, end, outputFile);
        try (TradingInputStream<Bar> bars = tradingSearch.aggregateFromTicks(symbol, period, start, end);
             BarInputStreamToCsv barsToCsv = new BarInputStreamToCsv(bars, new FileWriter(outputFile))) {
            barsToCsv.convert();
        }
        log.info("Wrote to file://{}", outputFile);
        log.info(tradingSearch.getCacheStatistics().cacheStats());
    }

    private Instant getRequiredValueInstant(ApplicationArguments args, String name) {
        return Instant.parse(getRequiredValue(args, name));
    }

    private static String getRequiredValue(ApplicationArguments args, String name) {
        if (!args.containsOption(name)) {
            throw new IllegalArgumentException("Missing command line argument --" + name + "=????");
        }
        List<String> optionValues = args.getOptionValues(name);
        if (optionValues == null || optionValues.isEmpty()) {
            throw new IllegalArgumentException("No value for command line argument --" + name + " Are you missing = ?");
        }
        return optionValues.getFirst();
    }
}
