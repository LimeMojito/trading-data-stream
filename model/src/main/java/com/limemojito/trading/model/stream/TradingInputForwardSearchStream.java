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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.NoSuchElementException;

@Slf4j
public final class TradingInputForwardSearchStream<Model> implements TradingInputStream<Model> {
    private final int maxCount;
    private final Search<Model> search;
    private int searchCount;
    private int givenCount;
    private TradingInputStream<Model> dataStream;

    public TradingInputForwardSearchStream(int maxCount, Search<Model> search) throws IOException {
        this.maxCount = maxCount;
        this.search = search;
        this.dataStream = search.perform(0);
    }

    @Override
    public void close() throws IOException {
        dataStream.close();
    }

    @Override
    public Model next() throws NoSuchElementException {
        Model next = dataStream.next();
        givenCount++;
        return next;
    }

    @Override
    @SneakyThrows
    public boolean hasNext() {
        if (givenCount < maxCount) {
            if (dataStream.hasNext()) {
                return true;
            } else {
                extendSearch();
                return dataStream.hasNext();
            }
        }
        return false;
    }

    public interface Search<Model> {
        TradingInputStream<Model> perform(int searchCount) throws IOException;
    }

    private void extendSearch() throws IOException {
        while (!dataStream.hasNext()) {
            dataStream.close();
            dataStream = search.perform(++searchCount);
        }
    }
}
