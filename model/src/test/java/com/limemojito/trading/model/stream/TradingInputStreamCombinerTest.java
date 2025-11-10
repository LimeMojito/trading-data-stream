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

package com.limemojito.trading.model.stream;

import com.limemojito.trading.model.TradingInputStream;
import com.limemojito.trading.model.tick.Tick;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static com.limemojito.trading.model.ModelPrototype.createTick;
import static com.limemojito.trading.model.StreamData.StreamSource.Historical;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("resource")
@Slf4j
public class TradingInputStreamCombinerTest {

    private final Tick peek = createTick("EURUSD", Instant.now().toEpochMilli(), 5678, Historical);
    @Mock
    private TradingInputStream<Tick> mockStream;
    @Mock
    private Iterator<TradingInputStream<Tick>> mockIterator;
    private TradingInputStreamCombiner<Tick> combiner;

    @BeforeEach
    public void setUp() {
        combiner = new TradingInputStreamCombiner<>(mockIterator, a -> true);
    }

    @AfterEach
    public void verifyMocks() {
        verifyNoMoreInteractions(mockIterator, mockStream);
    }

    @Test
    public void shouldNoSuchElementIfNoElementsWithIteratorScan() {
        doReturn(true, false).when(mockIterator).hasNext();
        doReturn(mockStream).when(mockIterator).next();
        doReturn(false).when(mockStream).hasNext();

        boolean hasNext = combiner.hasNext();
        assertThat(hasNext).isEqualTo(false);
        assertThatThrownBy(() -> combiner.next()).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void shouldHandleMultipleHasNextWithPeek() {
        whenFirstStreamLoaded();
        whenFirstDataPresent();

        assertThat(combiner.hasNext()).isEqualTo(true);
        assertThat(combiner.hasNext()).isEqualTo(true);
        assertThat(combiner.next()).isEqualTo(peek);
    }

    @Test
    public void shouldHandleNextWithoutHasNextLazyLoad() {
        whenFirstStreamLoaded();
        whenFirstDataPresent();

        assertThat(combiner.next()).isEqualTo(peek);
    }

    @Test
    public void shouldReturnDataPeekForFirstLoad() {
        whenFirstStreamLoaded();
        whenFirstDataPresent();

        boolean hasNext = combiner.hasNext();
        Tick tick = combiner.next();

        assertThat(hasNext).isEqualTo(true);
        assertThat(tick).isEqualTo(peek);
        verify(mockIterator).hasNext();
        verify(mockIterator).next();
        verify(mockStream).hasNext();
        verify(mockStream).next();
    }

    @Test
    public void shouldIterateTwoStreams() throws Exception {
        doReturn(true, true, false).when(mockIterator).hasNext();
        doReturn(mockStream).when(mockIterator).next();
        doReturn(true, false, true, false).when(mockStream).hasNext();
        doReturn(peek, peek).when(mockStream).next();

        int count = 0;
        for (Tick tick : combiner) {
            log.info("Found tick {}", tick);
            count++;
        }
        combiner.close();

        assertThat(count).isEqualTo(2);
        verify(mockStream, times(2)).close();
        verify(mockStream, times(2)).next();
    }

    @Test
    public void shouldClosePartIterate() throws Exception {
        whenFirstStreamLoaded();
        whenFirstDataPresent();

        assertThat(combiner.hasNext()).isTrue();
        combiner.close();

        verify(mockStream).next();
        verify(mockStream).close();
    }

    private void whenFirstStreamLoaded() {
        doReturn(true).when(mockIterator).hasNext();
        doReturn(mockStream).when(mockIterator).next();
    }

    private void whenFirstDataPresent() {
        doReturn(true).when(mockStream).hasNext();
        doReturn(peek).when(mockStream).next();
    }
}
