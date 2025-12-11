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

import tools.jackson.core.type.TypeReference;
import com.limemojito.json.JsonLoader;
import com.limemojito.json.JsonMapperPrototype;
import com.limemojito.trading.model.tick.dukascopy.DukascopyTickInputStream;
import com.limemojito.trading.model.tick.dukascopy.DukascopyUtils;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validator;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import static com.limemojito.trading.model.bar.Bar.Period.M5;
import static org.assertj.core.api.Assertions.assertThat;

public class TickToBarListTest {

    private final JsonLoader jsonLoader = new JsonLoader(JsonMapperPrototype.buildBootLikeMapper());
    private final TypeReference<List<Bar>> barListType = new TypeReference<>() {
    };

    @Test
    public void shouldReproduce1TickFailure() throws Exception {
        performLoadComparison("/EURUSD/2021/10/04/", "10h_ticks.bi5", "10h_M5_bars.json");
    }

    @Test
    public void shouldProduceNoDuplicatesNovember() throws Exception {
        performLoadComparison("/EURUSD/2021/10/01/", "04h_ticks.bi5", "04h_M5_bars.json");
    }

    @Test
    public void shouldProduceNoDuplicatesDecember() throws Exception {
        performLoadComparison("/EURUSD/2021/11/01/", "00h_ticks.bi5", "00h_M5_bars.json");
    }

    private void performLoadComparison(String resourcePackage, String ticks, String expected) throws IOException {
        final String path = resourcePackage + ticks;
        File tempLocation = DukascopyUtils.dukascopyClassResourceToTempFile(path);

        final Validator validator = DukascopyUtils.setupValidator();
        TickToBarList tickToBarList = new TickToBarList(validator,
                                                        M5,
                                                        new DukascopyTickInputStream(validator,
                                                                                     path,
                                                                                     new FileInputStream(tempLocation)));
        final List<Bar> convert = tickToBarList.convert();

        assertThat(convert).hasSize(12);
        assertThat(convert).isEqualTo(jsonLoader.loadFrom(resourcePackage + expected, barListType));
    }
}
