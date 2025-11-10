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

package com.limemojito.trading.model.tick.dukascopy;

import com.limemojito.trading.model.TradingInputStream;
import com.limemojito.trading.model.tick.Tick;
import com.limemojito.trading.model.tick.TickVisitor;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.limemojito.trading.model.StreamData.REALTIME_UUID;
import static com.limemojito.trading.model.StreamData.StreamSource.Historical;
import static java.lang.String.format;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.time.ZoneOffset.UTC;

/**
 * Streaming reader over Dukascopy compressed tick files.
 * <p>
 * Lazily opens the underlying LZMA-compressed input when first needed, decodes fixed-size
 * binary tick rows, validates them, and optionally forwards each tick to a visitor.
 * </p>
 */
@Slf4j
public class DukascopyTickInputStream implements TradingInputStream<Tick> {
    private static final int TICK_ROW_SIZE = 20;
    /**
     * Note that the month is ZERO INDEXED for the dukascopy format
     */
    private static final Pattern PATH_PATTERN = Pattern.compile("/(\\d{4})/(\\d{2})/(\\d{2})/(\\d{2})");
    private final Validator validator;
    private final ByteBuffer buffer;
    private final String symbol;
    private final long epochGmt;
    private final DukascopyCache cache;
    private final InputStream directSuppliedInputStream;
    private final String path;
    private final TickVisitor visitor;
    private boolean readAttempted;
    private LZMACompressorInputStream delegate;
    private Tick pushback;

    /**
     * @param validator Validator to use to check data
     * @param path      A valid dukascopy path: ie EURUSD/2018/06/05/05h_ticks.bi
     * @param cache     Caching strategy to use on data.
     */
    public DukascopyTickInputStream(Validator validator, DukascopyCache cache, String path) {
        this(validator, path, null, cache, TickVisitor.NO_VISITOR);
    }

    /**
     * @param validator Validator to use to check data
     * @param path      A valid dukascopy path: ie EURUSD/2018/06/05/05h_ticks.bi
     * @param cache     Caching strategy to use on data.
     * @param visitor   Visitor to see ticks as being streamed.
     */
    public DukascopyTickInputStream(Validator validator, DukascopyCache cache, String path, TickVisitor visitor) {
        this(validator, path, null, cache, visitor);
    }

    /**
     * @param validator   Validator to use to check data
     * @param path        A valid dukascopy path: ie EURUSD/2018/06/05/05h_ticks.bi
     * @param inputStream The file data that matches the path.
     */
    public DukascopyTickInputStream(Validator validator, String path, InputStream inputStream) {
        this(validator, path, inputStream, null, TickVisitor.NO_VISITOR);
    }

    /**
     * @param validator   Validator to use to check data
     * @param path        A valid dukascopy path: ie EURUSD/2018/06/05/05h_ticks.bi
     * @param inputStream The file data that matches the path.
     * @param visitor     Visitor to see ticks as being streamed.
     */
    public DukascopyTickInputStream(Validator validator, String path, InputStream inputStream, TickVisitor visitor) {
        this(validator, path, inputStream, null, visitor);
    }

    /**
     * Whether another tick can be read from the underlying Dukascopy stream.
     * <p>
     * Lazily opens the compressed input on first invocation and prefetches one tick to determine availability.
     * </p>
     *
     * @return {@code true} if a subsequent call to {@link #next()} will succeed
     */
    @Override
    @SneakyThrows
    public boolean hasNext() {
        lazyLoad();
        if (pushback != null) {
            return true;
        }
        if (delegate == null) {
            return false;
        }
        pushback = readTick();
        if (pushback == null) {
            return false;
        }
        return hasNext();
    }

    /**
     * Returns the next decoded tick from the stream.
     *
     * @return next {@link Tick}
     * @throws java.util.NoSuchElementException if the end of the stream has been reached
     */
    @Override
    @SneakyThrows
    public Tick next() {
        Tick tick = readTick();
        if (tick == null) {
            throw new NoSuchElementException("No more ticks from " + path);
        }
        return tick;
    }

    /**
     * Closes the underlying compressed stream if it has been opened.
     *
     * @throws IOException if an I/O error occurs while closing
     */
    public void close() throws IOException {
        if (delegate != null) {
            delegate.close();
        }
    }


    private DukascopyTickInputStream(Validator validator,
                                     String path,
                                     InputStream directSuppliedInputStream,
                                     DukascopyCache cache,
                                     TickVisitor visitor) {
        this.validator = validator;
        this.path = path;
        this.visitor = visitor;
        final int symbolEndIndex = path.indexOf("/2");
        final String datePath = path.substring(symbolEndIndex);
        this.symbol = parseSymbol(path, symbolEndIndex);
        this.epochGmt = parseGmtStart(datePath);
        this.buffer = ByteBuffer.allocate(TICK_ROW_SIZE).order(BIG_ENDIAN);
        this.directSuppliedInputStream = directSuppliedInputStream;
        this.cache = cache;
    }

    private Tick readTick() throws IOException {
        lazyLoad();
        if (pushback != null) {
            Tick read = pushback;
            pushback = null;
            return read;
        }
        if (delegate == null) {
            return null;
        }
        int read = delegate.read(buffer.array());
        if (read == -1) {
            log.trace("End of stream reached");
            return null;
        } else if (read != buffer.capacity()) {
            throw new IOException(format("Corrupted data - read %d expected %d", read, buffer.capacity()));
        }
        final Tick tick = bufferToTick();
        log.trace("Found tick {}", tick);
        visitor.visit(tick);
        return tick;
    }

    private void lazyLoad() throws IOException {
        if (!readAttempted) {
            final InputStream inputStream = (directSuppliedInputStream
                                             != null) ? directSuppliedInputStream : cache.stream(
                    path);
            try {
                delegate = new LZMACompressorInputStream(inputStream);
            } catch (EOFException e) {
                log.debug("0 length file encountered");
            }
            readAttempted = true;
        }
    }

    private Tick bufferToTick() {
        buffer.rewind();
        final Tick tick = Tick.builder()
                              .streamId(REALTIME_UUID)
                              .symbol(symbol)
                              .millisecondsUtc(buffer.getInt() + epochGmt)
                              .ask(buffer.getInt())
                              .bid(buffer.getInt())
                              .askVolume(buffer.getFloat())
                              .bidVolume(buffer.getFloat())
                              .source(Historical)
                              .build();
        final Set<ConstraintViolation<Tick>> constraintViolations = validator.validate(tick);
        if (!constraintViolations.isEmpty()) {
            throw new ConstraintViolationException(constraintViolations);
        }
        return tick;
    }

    private static long parseGmtStart(String datePath) {
        final Matcher matcher = PATH_PATTERN.matcher(datePath);
        if (matcher.find()) {
            final LocalDateTime gmtTime = LocalDateTime.of(intAt(matcher, 1),
                                                           intAt(matcher, 2) + 1,
                                                           intAt(matcher, 3),
                                                           intAt(matcher, 4),
                                                           0);
            log.debug("Found gmt epoch of {}", gmtTime);
            return gmtTime.toInstant(UTC).toEpochMilli();
        } else {
            throw new IllegalArgumentException("Can not parse path " + datePath);
        }
    }

    private static int intAt(Matcher matcher, int index) {
        return Integer.parseInt(matcher.group(index));
    }

    private static String parseSymbol(String path, int symbolEndIndex) {
        final int symbolStartIndex = path.substring(0, symbolEndIndex).indexOf('/') + 1;
        final String aSymbol = path.substring(symbolStartIndex, symbolEndIndex);
        log.debug("Found symbol {} from {}", aSymbol, path);
        return aSymbol;
    }
}
