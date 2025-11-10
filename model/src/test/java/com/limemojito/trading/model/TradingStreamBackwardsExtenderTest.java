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

package com.limemojito.trading.model;

import com.limemojito.trading.model.bar.Bar;
import com.limemojito.trading.model.bar.BarListInputStream;
import com.limemojito.trading.model.bar.BarVisitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static com.limemojito.trading.model.ModelPrototype.createBarListDescending;
import static com.limemojito.trading.model.StreamData.REALTIME_UUID;
import static com.limemojito.trading.model.bar.Bar.Period.H1;
import static com.limemojito.trading.model.bar.BarVisitor.NO_VISITOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@SuppressWarnings("resource")
@ExtendWith(MockitoExtension.class)
public class TradingStreamBackwardsExtenderTest {

    private final String symbol = "EURUSD";
    private final Bar.Period period = H1;
    private final Instant endTime = Instant.parse("2021-11-01T09:00:00Z");
    private final BarVisitor visitor = NO_VISITOR;
    @Mock
    private TradingSearch search;

    @Test
    public void shouldStreamBackwardsExtendingSearch() throws Exception {
        when(search.getTheBeginningOfTime()).thenReturn(Instant.parse("2018-01-01T00:00:00Z"));
        findBars("2021-10-28T05:00:00Z", endTime.toString(), 1);
        final int barCountBefore = 100;
        findBars("2021-10-24T01:00:00Z", "2021-10-28T05:00:00Z", barCountBefore - 1);

        TradingInputStream<Bar> backwards = TradingInputStreamBackwardsExtender.extend(symbol,
                                                                                       period,
                                                                                       barCountBefore,
                                                                                       endTime,
                                                                                       visitor,
                                                                                       search);

        assertThat(backwards).isNotNull();
        assertThat(backwards).hasSize(barCountBefore);
    }

    @Test
    @SuppressWarnings("InstantiationOfUtilityClass")
    public void shouldCover() {
        new TradingInputStreamBackwardsExtender();
    }
    private void findBars(String startTime, String endTime, int numBars) throws IOException {
        Instant beforeInstant = Instant.parse(endTime).minusNanos(1);
        List<Bar> firstBars = createBarListDescending(REALTIME_UUID, symbol, period, beforeInstant.toEpochMilli(), numBars);
        doReturn(new BarListInputStream(firstBars, visitor))
                .when(search)
                .aggregateFromTicks(symbol, period, Instant.parse(startTime), beforeInstant, visitor);
    }
}
