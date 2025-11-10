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

package com.limemojito.trading.model.stream;

import com.limemojito.trading.model.TradingInputStream;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

public abstract class TradingInputStreamMapper {

    public interface Visitor<MODEL> {
        void visit(MODEL bar);
    }

    public static <MODEL> TradingInputStream<MODEL> streamFrom(Collection<MODEL> modelData) {
        return streamFrom(modelData, null, null);
    }

    public static <MODEL> TradingInputStream<MODEL> streamFrom(Collection<MODEL> modelData, Visitor<MODEL> visitor) {
        return streamFrom(modelData, visitor, null);
    }

    public static <MODEL> TradingInputStream<MODEL> streamFrom(Collection<MODEL> modelData,
                                                               Visitor<MODEL> visitor,
                                                               Runnable onClose) {
        return new CollectionStream<>(modelData, visitor, onClose);
    }

    public interface Transformer<MODEL, TRANSFORMED> {
        TRANSFORMED transform(MODEL model);
    }

    public static <MODEL, TRANSFORMED> TradingInputStream<TRANSFORMED> map(TradingInputStream<MODEL> dataStream,
                                                                           Transformer<MODEL, TRANSFORMED> transformer) {
        return map(dataStream, transformer, () -> {
        });
    }

    public static <MODEL, TRANSFORMED> TradingInputStream<TRANSFORMED> map(TradingInputStream<MODEL> dataStream,
                                                                           Transformer<MODEL, TRANSFORMED> transformer,
                                                                           Runnable onClose) {
        return new DelegatingStream<>(transformer, dataStream, onClose);
    }

    @Slf4j
    private static final class DelegatingStream<MODEL, TRANSFORMED> implements TradingInputStream<TRANSFORMED> {
        private final Transformer<MODEL, TRANSFORMED> transformer;
        private final TradingInputStream<MODEL> dataStream;
        private final Runnable onClose;

        private DelegatingStream(Transformer<MODEL, TRANSFORMED> transformer,
                                 TradingInputStream<MODEL> dataStream,
                                 Runnable onClose) {
            this.transformer = transformer;
            this.dataStream = dataStream;
            this.onClose = onClose;
        }

        @Override
        public TRANSFORMED next() throws NoSuchElementException {
            return transformer.transform(dataStream.next());
        }

        @Override
        public boolean hasNext() {
            return dataStream.hasNext();
        }

        @Override
        public void close() throws IOException {
            if (onClose != null) {
                try {
                    onClose.run();
                } catch (Throwable e) {
                    log.warn("Failure in on close handler for data stream {}", e.getMessage(), e);
                }
            }
            dataStream.close();
        }
    }

    @Slf4j
    private static final class CollectionStream<MODEL> implements TradingInputStream<MODEL> {
        private final Iterator<MODEL> iterator;
        private final Visitor<MODEL> visitor;
        private final Runnable onClose;

        private CollectionStream(Collection<MODEL> modelData, Visitor<MODEL> visitor, Runnable onClose) {
            this.iterator = modelData.iterator();
            this.visitor = visitor;
            this.onClose = onClose;
        }

        @Override
        public MODEL next() throws NoSuchElementException {
            MODEL next = iterator.next();
            if (visitor != null) {
                visitor.visit(next);
            }
            return next;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public void close() {
            if (onClose != null) {
                onClose.run();
            }
        }
    }
}
