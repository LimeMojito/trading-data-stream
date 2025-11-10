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

import org.junit.jupiter.api.Test;

import static com.limemojito.trading.model.bar.Bar.Period.D1;
import static com.limemojito.trading.model.bar.Bar.Period.H1;
import static com.limemojito.trading.model.bar.Bar.Period.H4;
import static com.limemojito.trading.model.bar.Bar.Period.M10;
import static com.limemojito.trading.model.bar.Bar.Period.M15;
import static com.limemojito.trading.model.bar.Bar.Period.M30;
import static com.limemojito.trading.model.bar.Bar.Period.M5;
import static org.assertj.core.api.Assertions.assertThat;

public class DurationTest {

    @Test
    public void shouldHaveDurationsCorrect() {
        assertThat(M5.getDurationMilliseconds()).isEqualTo(300_000L);
        assertThat(M10.getDurationMilliseconds()).isEqualTo(600_000L);
        assertThat(M15.getDurationMilliseconds()).isEqualTo(900_000L);
        assertThat(M30.getDurationMilliseconds()).isEqualTo(1_800_000L);
        assertThat(H1.getDurationMilliseconds()).isEqualTo(3_600_000L);
        assertThat(H4.getDurationMilliseconds()).isEqualTo(14_400_000L);
        assertThat(D1.getDurationMilliseconds()).isEqualTo(86_400_000L);
    }


}
