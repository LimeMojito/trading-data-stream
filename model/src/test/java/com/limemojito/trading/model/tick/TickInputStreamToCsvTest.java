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

package com.limemojito.trading.model.tick;

import com.limemojito.trading.model.TradingInputStream;
import com.limemojito.trading.model.tick.dukascopy.DukascopyTickInputStream;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.*;

import static com.limemojito.trading.model.tick.dukascopy.DukascopyUtils.dukascopyClassResourceToTempFile;
import static com.limemojito.trading.model.tick.dukascopy.DukascopyUtils.setupValidator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
public class TickInputStreamToCsvTest {

    private final Validator validator = setupValidator();
    private final String tickPath = "/EURUSD/2018/06/05/05h_ticks";

    @Test
    public void shouldCopyResourceToTempLocation() throws Exception {
        final String path = "/EURUSD/2018/06/05/05h_ticks.bi5";
        File tempLocation = dukascopyClassResourceToTempFile(path);
        assertThat(tempLocation).isFile();
        assertThat(tempLocation).hasSize(33117L);
        assertThat(tempLocation.getAbsolutePath()).isEqualTo(new File(System.getProperty("java.io.tmpdir"),
                                                                      path).getAbsolutePath());
    }

    @Test
    public void shouldFailIfClasspathResourceMissing() {
        final String path = "/EURBCD/2018/06/05/05h_ticks.bi5";
        assertThatThrownBy(() -> dukascopyClassResourceToTempFile(path)).isInstanceOf(IOException.class)
                                                                        .hasMessage(
                                                                                "Could not open classpath resource /EURBCD/2018/06/05/05h_ticks.bi5");

    }

    @Test
    public void shouldProcessToCsvOkByWriter() throws Exception {
        final String dukascopyPath = tickPath + ".bi5";
        try (
                FileInputStream inputStream = new FileInputStream(dukascopyClassResourceToTempFile(dukascopyPath));
                FileWriter outputWriter = new FileWriter(csvOutputFile());
                TradingInputStream<Tick> ticks = new DukascopyTickInputStream(validator,
                                                                              dukascopyPath,
                                                                              inputStream);
                TickInputStreamToCsv csv = new TickInputStreamToCsv(ticks, outputWriter)
        ) {
            csv.convert();
        }

        assertOutputOk(csvOutputFile());
    }

    @Test
    public void shouldProcessToCsvOkByOutputStream() throws Exception {
        final String dukascopyPath = tickPath + ".bi5";
        try (
                FileInputStream inputStream = new FileInputStream(dukascopyClassResourceToTempFile(dukascopyPath));
                FileOutputStream outputStream = new FileOutputStream(csvOutputFile());
                TradingInputStream<Tick> ticks = new DukascopyTickInputStream(validator,
                                                                              dukascopyPath,
                                                                              inputStream);
                TickInputStreamToCsv csv = new TickInputStreamToCsv(ticks, outputStream)
        ) {
            csv.convert();
        }

        assertOutputOk(csvOutputFile());
    }


    private static File csvOutputFile() {
        return new File(System.getProperty("java.io.tmpdir"), "eurusd-2018-06-05-05h.csv");
    }

    private void assertOutputOk(File csvOutput) throws IOException {
        log.info("CSV Output to file://{}", csvOutput.getAbsolutePath());
        InputStream expectedCsvData = getClass().getResourceAsStream(tickPath + ".csv");
        assertThat(expectedCsvData).isNotNull();
        assertThat(csvOutput).isFile();
        assertThat(csvOutput).hasSize(269623L);
        assertThat(csvOutput).hasContent(new String(IOUtils.toByteArray(expectedCsvData), UTF_8));
    }
}
