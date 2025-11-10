/*
 * Copyright 2011-2024 Lime Mojito Pty Ltd
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

import com.google.common.collect.Streams;
import com.limemojito.trading.model.stream.TradingInputStreamCombiner;
import lombok.RequiredArgsConstructor;

import java.io.Closeable;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Pull-based iterator-like API for consuming trading data models (e.g. ticks, bars).
 * <p>
 * Implementations expose {@link #next()} and {@link #hasNext()} similar to {@link Iterator},
 * and can be consumed using enhanced for-loops via {@link #iterator()} or converted to a
 * Java {@link Stream} using {@link #stream()}.
 *
 * @param <Model> the element type produced by this stream
 */
public interface TradingInputStream<Model> extends Closeable, Iterable<Model> {
    /**
     * Returns the next element in the stream.
     *
     * @return the next element; may be {@code null} at end of stream depending on implementation
     * @throws NoSuchElementException if the stream is exhausted and does not return {@code null}
     */
    Model next() throws NoSuchElementException;

    /**
     * Whether another element is available from {@link #next()}.
     *
     * @return true if at least one more element can be read
     */
    boolean hasNext();

    /**
     * Iterator view over this input stream enabling enhanced for-loops.
     */
    default Iterator<Model> iterator() {
        return new TradingInputStream.ModelIterator<>(this);
    }

    /**
     * Returns a lazily-evaluated Java {@link Stream} backed by this input stream.
     */
    default Stream<Model> stream() {
        return Streams.stream(this);
    }

    /**
     * Simple adapter implementing {@link Iterator} over a {@link TradingInputStream}.
     */
    @RequiredArgsConstructor
    class ModelIterator<Model> implements Iterator<Model> {
        private final TradingInputStream<Model> inputStream;

        @Override
        public boolean hasNext() {
            return inputStream.hasNext();
        }

        @Override
        public Model next() throws NoSuchElementException {
            return inputStream.next();
        }
    }

    /**
     * Combine multiple input streams of the same element type into a single stream that
     * reads from each in sequence.
     *
     * @param inputStreams streams to combine
     * @return a composite input stream
     */
    static <Model> TradingInputStream<Model> combine(Collection<TradingInputStream<Model>> inputStreams) {
        return combine(inputStreams.iterator());
    }

    /**
     * Combine multiple input streams of the same element type into a single stream that
     * reads from each in sequence.
     *
     * @param inputStreams streams to combine
     * @return a composite input stream
     */
    static <Model> TradingInputStream<Model> combine(Iterator<TradingInputStream<Model>> inputStreams) {
        return combine(inputStreams, a -> true);
    }

    /**
     * Combine multiple input streams of the same element type into a single stream applying
     * a filter to elements as they are read.
     *
     * @param inputStreams streams to combine
     * @param filter       predicate to filter elements; elements for which this returns false are skipped
     * @return a filtered composite input stream
     */
    static <Model> TradingInputStream<Model> combine(Collection<TradingInputStream<Model>> inputStreams,
                                                     Predicate<Model> filter) {
        return combine(inputStreams.iterator(), filter);
    }

    /**
     * Combine multiple input streams of the same element type into a single stream applying
     * a filter to elements as they are read.
     *
     * @param inputStreams streams to combine
     * @param filter       predicate to filter elements; elements for which this returns false are skipped
     * @return a filtered composite input stream
     */
    static <Model> TradingInputStream<Model> combine(Iterator<TradingInputStream<Model>> inputStreams,
                                                     Predicate<Model> filter) {
        return new TradingInputStreamCombiner<>(inputStreams, filter);
    }
}
