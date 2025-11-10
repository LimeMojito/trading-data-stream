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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SuppressWarnings("resource")
@ExtendWith(MockitoExtension.class)
public class DukascopyCachePrimerTest {

    @Mock
    private DukascopyPathGenerator pathGenerator;

    @Mock
    private DukascopyCache cache;

    @InjectMocks
    private DukascopyCachePrimer primer;

    @Test
    public void shouldPerformNewLoad() throws Exception {
        Instant end = Instant.parse("2020-04-01T00:00:00Z");
        Instant start = Instant.parse("2020-03-01T00:00:00Z");
        String symbol = "AUDUSD";
        String dukascopyPath = "aPath";
        doReturn(List.of(dukascopyPath)).when(pathGenerator).generatePaths(symbol, start, end);
        doReturn(new ByteArrayInputStream("hello".getBytes(UTF_8))).when(cache).stream(dukascopyPath);

        primer.newLoad();
        primer.load(symbol, start, end);
        primer.waitForCompletion();
        primer.shutdown();

        verify(pathGenerator).generatePaths(symbol, start, end);
        verify(cache).stream(dukascopyPath);
    }

    @Test
    public void shouldContinueProcessingOnFailure() throws Exception {
        Instant end = Instant.parse("2020-04-01T00:00:00Z");
        Instant start = Instant.parse("2020-03-01T00:00:00Z");
        String symbol = "AUDUSD";
        String dukascopyPath = "aPath";
        doReturn(List.of(dukascopyPath)).when(pathGenerator).generatePaths(symbol, start, end);
        doThrow(IOException.class).when(cache).stream(dukascopyPath);

        primer.newLoad();
        primer.load(symbol, start, end);
        primer.waitForCompletion();
        primer.shutdown();

        verify(pathGenerator).generatePaths(symbol, start, end);
        verify(cache).stream(dukascopyPath);
    }
}
