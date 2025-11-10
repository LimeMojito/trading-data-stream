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

package com.limemojito.trading.model.bar;

import com.limemojito.trading.model.TradingInputStream;
import com.limemojito.trading.model.tick.Tick;
import com.limemojito.trading.model.tick.dukascopy.DukascopyTickInputStream;
import com.limemojito.trading.model.tick.dukascopy.cache.DirectDukascopyNoCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.Validator;
import java.util.NoSuchElementException;

import static com.limemojito.trading.model.bar.Bar.Period.M5;
import static com.limemojito.trading.model.tick.dukascopy.DukascopyUtils.setupValidator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TickToBarInputStreamTest {

    private static final Validator VALIDATOR = setupValidator();

    @Mock
    private TradingInputStream<Tick> tickInputStream;

    @AfterEach
    public void verifyMocks() {
        verifyNoMoreInteractions(tickInputStream);
    }

    @Test
    public void shouldReturnFalseForHasNextOnEmptyTickStream() throws Exception {
        doReturn(false).when(tickInputStream).hasNext();

        try (TickToBarInputStream tickToBarInputStream = create()) {
            assertThatThrownBy(tickToBarInputStream::next).isInstanceOf(NoSuchElementException.class);
        }

        verify(tickInputStream).close();
    }

    @Test
    public void shouldThrowNoSuchElementException() throws Exception {
        doReturn(false).when(tickInputStream).hasNext();

        try (TickToBarInputStream tickToBarInputStream = create()) {
            assertThatThrownBy(tickToBarInputStream::next).isInstanceOf(NoSuchElementException.class);
        }

        verify(tickInputStream).close();
    }

    @Test
    public void shouldNotProduceABarForEmptyTicks() throws Exception {
        String path = "EURUSD/2018/11/30/19h_ticks.bi5";
        try (DukascopyTickInputStream ticks = new DukascopyTickInputStream(VALIDATOR,
                                                                           new DirectDukascopyNoCache(),
                                                                           path);
             TickToBarInputStream barStream = new TickToBarInputStream(VALIDATOR, M5, ticks)) {
            assertThat(barStream.hasNext()).isFalse();
        }
    }

    private TickToBarInputStream create() {
        return new TickToBarInputStream(VALIDATOR, M5, tickInputStream);
    }
}
