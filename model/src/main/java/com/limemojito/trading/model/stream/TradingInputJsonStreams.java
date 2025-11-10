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


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limemojito.trading.model.TradingInputStream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Utilities for streaming JSON serialization and deserialization of model objects using Jackson.
 * <p>
 * Provides helpers to write a {@link com.limemojito.trading.model.TradingInputStream} or a {@link java.util.Collection}
 * as a JSON array without loading all data in memory, and to create an input stream that lazily reads
 * a JSON array from an {@link java.io.InputStream} one object at a time.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class TradingInputJsonStreams {
    private final ObjectMapper mapper;

    /**
     * Write the supplied input stream to the output stream using Jackson in ARRAY format.  The output is a streamed write one model object
     * at a time.  Note there is a streaming version of this method which would be more efficient than loading a collection into memory.
     *
     * @param <Model>      Model object to write (needs to be "Jsonable").
     * @param collection   Collection to stream data from.
     * @param outputStream output stream to write to.
     * @throws IOException on an IO failure.
     * @see #writeAsJsonArray(TradingInputStream, OutputStream)
     */
    public <Model> void writeAsJsonArray(Collection<Model> collection, OutputStream outputStream) throws IOException {
        writeFromIterator(outputStream, collection.iterator());
    }

    /**
     * Write the supplied input stream to the output stream using Jackson in ARRAY format.  The output is a streamed write one model object
     * at a time.
     *
     * @param <Model>      Model object to write (needs to be "Jsonable").
     * @param inputStream  Model stream to write out
     * @param outputStream output stream to write to.
     * @throws IOException on an IO failure.
     */
    public <Model> void writeAsJsonArray(TradingInputStream<Model> inputStream, OutputStream outputStream) throws
                                                                                                           IOException {
        writeFromIterator(outputStream, inputStream.iterator());
    }

    /**
     * Create an input stream for the supplied model using expected json data from the input stream in ARRAY format.
     * Read is streamed one object at a time.
     *
     * @param <Model>     Model object to read (needs to be "Jsonable").
     * @param inputStream Model stream to read
     * @param type        class instance of the expected model type (to support Jackson json read).
     * @return An input stream ready to stream read data.
     * @throws IOException on a creation failure.
     */
    public <Model> TradingInputStream<Model> createStream(InputStream inputStream, Class<Model> type) throws
                                                                                                      IOException {
        return createStream(inputStream, type, (model) -> {
        });
    }

    /**
     * Create an input stream for the supplied model using expected json data from the input stream in ARRAY format.
     * Read is streamed one object at a time.  Apply a visitor to the object when it is restored.
     *
     * @param <Model>     Model object to read (needs to be "Jsonable").
     * @param inputStream Model stream to read
     * @param type        class instance of the expected model type (to support Jackson json read).
     * @param visitor     Visitor to apply to reconstituted model object.
     * @return An input stream ready to stream read data.
     * @throws IOException on a creation failure.
     */
    public <Model> TradingInputStream<Model> createStream(InputStream inputStream,
                                                          Class<Model> type,
                                                          TradingInputStreamMapper.Visitor<Model> visitor) throws
                                                                                                           IOException {
        return new JsonTradingInputStream<>(inputStream, mapper, type, visitor);
    }

    private <Model> void writeFromIterator(OutputStream outputStream, Iterator<Model> iterator) throws IOException {
        outputStream.write("[".getBytes(UTF_8));
        while (iterator.hasNext()) {
            Model next = iterator.next();
            // work around auto-close if jackson passed a stream.
            outputStream.write(mapper.writeValueAsBytes(next));
            if (iterator.hasNext()) {
                outputStream.write(",".getBytes(UTF_8));
            }
        }
        outputStream.write("]".getBytes(UTF_8));
    }

    private static class JsonTradingInputStream<Model> implements TradingInputStream<Model> {
        private final Class<Model> type;
        private final JsonParser jsonParser;
        private final TradingInputStreamMapper.Visitor<Model> visitor;
        private Model peek;

        JsonTradingInputStream(InputStream inputStream,
                               ObjectMapper mapper,
                               Class<Model> type,
                               TradingInputStreamMapper.Visitor<Model> visitor) throws IOException {
            this.visitor = visitor;
            // work around auto-close if jackson passed a stream.
            this.jsonParser = mapper.createParser(inputStream);
            this.type = type;
        }

        @Override
        public Model next() throws NoSuchElementException {
            if (peek == null) {
                peek();
            }
            if (peek == null) {
                throw new NoSuchElementException();
            }
            Model next = peek;
            peek = null;
            return next;
        }

        @Override
        public boolean hasNext() {
            if (peek != null) {
                return true;
            }
            peek();
            return peek != null;
        }

        @Override
        public void close() throws IOException {
            jsonParser.close();
        }

        /**
         * at start of file, first element is '['.
         * in between records, element will be ','
         * at end of file, element will be ']'
         * ignore whitespace.
         */
        @SneakyThrows
        private void peek() {
            JsonToken nextToken;
            do {
                nextToken = jsonParser.nextToken();
            } while (nextToken != null && nextToken != JsonToken.START_OBJECT);
            if (nextToken != null) {
                peek = jsonParser.readValueAs(type);
                visitor.visit(peek);
            } else {
                peek = null;
            }
        }
    }
}
