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

import com.limemojito.trading.model.bar.Bar.Period;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.limemojito.trading.model.bar.Bar.Period.D1;
import static com.limemojito.trading.model.bar.Bar.Period.H4;
import static com.limemojito.trading.model.bar.Bar.Period.M10;
import static com.limemojito.trading.model.bar.Bar.Period.M15;
import static com.limemojito.trading.model.bar.Bar.Period.M30;
import static com.limemojito.trading.model.bar.Bar.Period.M5;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PeriodSizingTest {

    @Test
    public void shouldFindLargest() {
        assertThat(Period.largest(List.of(M5, M10, D1, H4))).isEqualTo(D1);
    }

    @Test
    public void shouldFindSmallest() {
        assertThat(Period.smallest(List.of(M30, M15, M5, M10, D1, H4))).isEqualTo(M5);
    }

    @Test
    public void shouldFailWhenEmptyListPassedToLargest() {
        assertThatThrownBy(() -> Period.largest(emptyList())).isInstanceOf(IllegalStateException.class)
                                                             .hasMessage("Supplied period list must not be empty");
    }

    @Test
    public void shouldFailWhenEmptyListPassedToSmallest() {
        assertThatThrownBy(() -> Period.smallest(emptyList())).isInstanceOf(IllegalStateException.class)
                                                              .hasMessage("Supplied period list must not be empty");
    }
}
