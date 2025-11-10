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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;

/**
 * Converts models to Excel format friendly CSV using apache commons csv. Note that LocalDataTime may be used to
 * format output that is an Excel friendly format ("2018-07-05 05:00:00").  Otherwise, use Instant will
 * output a standard ISO (1970-01-01T00:00:00Z) format.
 *
 * @param <Model> Type of object that the concrete class will convert.
 * @see CSVPrinter
 * @see Instant
 * @see LocalDateTime
 */
@Slf4j
public abstract class TradingCsvStream<Model> implements AutoCloseable {
    private static final String UNKNOWN = "NONE";
    private final TradingInputStream<Model> inputStream;
    private final CSVPrinter printer;

    /**
     * Construct a CSV converter writing to a binary {@link OutputStream} using UTF-8 encoding.
     *
     * @param inputStream  source of models to read
     * @param outputStream destination to write CSV bytes to
     * @throws IOException if the printer cannot be created
     */
    public TradingCsvStream(TradingInputStream<Model> inputStream, OutputStream outputStream) throws IOException {
        this(inputStream, new OutputStreamWriter(outputStream, UTF_8));
    }

    /**
     * Construct a CSV converter writing to a character {@link Writer}.
     *
     * @param inputStream source of models to read
     * @param output      destination to write CSV text to
     * @throws IOException if the printer cannot be created
     */
    public TradingCsvStream(TradingInputStream<Model> inputStream, Writer output) throws IOException {
        this.inputStream = inputStream;
        this.printer = new CSVPrinter(output, CSVFormat.EXCEL);
    }

    /**
     * Close both the underlying input stream and the CSV printer.
     *
     * @throws IOException if closing either resource fails
     */
    @Override
    public void close() throws IOException {
        inputStream.close();
        printer.close();
    }

    /**
     * Stream all models from the input and write them as CSV records.
     * The header is emitted first using {@link #getHeader()} and each model is converted
     * via {@link #modelToFields(Object)} with light formatting applied for date/time values.
     *
     * @throws IOException if writing fails
     */
    public void convert() throws IOException {
        log.info("Begin convert to CSV");
        printer.printRecord(getHeader());
        printer.printComment("Generated at " + ZonedDateTime.now());
        long count = 0L;
        String modelName = UNKNOWN;
        while (inputStream.hasNext()) {
            Model next = inputStream.next();
            count++;
            modelName = determineModelName(modelName, next);
            printer.printRecord(formatIntercept(modelToFields(next)));
        }
        log.info("Converted {} {}(s)", count, modelName);
    }

    /**
     * Header in order.
     *
     * @return a List of strings to use as the CSV header.
     */
    protected abstract List<String> getHeader();

    /**
     * Supply a list of fields base on the model in header order.  Prefer Object tp primitive types though boxing
     * conversions should be fine.  Note there is an intercept in place for LocalDateTime so that our format,
     * "2018-07-05 05:00:00" is used instead.  You can perform your own interceptions by formatting your data as a
     * string before returning the value list as String is passed through (other than CSV escaping).
     *
     * @param model Data instance to convert.
     * @return a List of strings to use as the CSV header.
     */
    protected abstract List<Object> modelToFields(Model model);

    private List<Object> formatIntercept(List<Object> modelToFields) {
        return modelToFields.stream().map(this::formatIntercept).collect(Collectors.toList());
    }

    private Object formatIntercept(Object data) {
        if (data instanceof LocalDateTime) {
            return formatToString((LocalDateTime) data);
        }
        return data;
    }

    private String formatToString(LocalDateTime dateTimeUtc) {
        return ISO_LOCAL_DATE.format(dateTimeUtc) + " " + ISO_LOCAL_TIME.format(dateTimeUtc);
    }

    private String determineModelName(String modelName, Model next) {
        //noinspection StringEquality
        if (UNKNOWN == modelName) {
            modelName = next.getClass().getSimpleName();
        }
        return modelName;
    }
}
