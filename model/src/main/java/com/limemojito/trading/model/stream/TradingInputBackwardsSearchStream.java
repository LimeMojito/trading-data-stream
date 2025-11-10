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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Materializes results from a backwards search into a bounded in-memory list and exposes them as a
 * {@link TradingInputStream} in forward iteration order.
 * <p>
 * The supplied {@link Search} strategy is invoked repeatedly until enough items are collected or
 * the search indicates there is no more data. Collected items are then sorted and trimmed to the
 * requested maximum before iteration begins.
 * </p>
 *
 * @param <Model> element type being streamed
 */
@Slf4j
public final class TradingInputBackwardsSearchStream<Model> implements TradingInputStream<Model> {

    private final Iterator<Model> dataIterator;

    /**
     * Strategy contract used by {@link TradingInputBackwardsSearchStream} to fetch and organize data chunks.
     *
     * @param <Model> element type being fetched
     */
    public interface Search<Model> {
        /**
         * Prepare for the next search call.
         *
         * @param searchCount zero-based count of searches performed so far
         * @return true if this will be the final search (no further attempts), false to continue if needed
         */
        boolean prepare(int searchCount);

        /**
         * Execute the search and return a stream of results for this slice.
         *
         * @return a stream of results for the current search slice
         * @throws IOException on search failure
         */
        TradingInputStream<Model> perform() throws IOException;

        /**
         * Sort the accumulated data in-place so that the final stream can iterate in forward order.
         *
         * @param data accumulated results to be sorted
         */
        void sort(List<Model> data);
    }

    /**
     * Create a stream that accumulates at most {@code maxCount} items by repeatedly invoking the provided search.
     *
     * @param maxCount maximum number of items to expose from the combined search
     * @param search   search strategy to retrieve and sort data
     * @throws IOException if a search attempt fails
     */
    public TradingInputBackwardsSearchStream(int maxCount, Search<Model> search) throws IOException {
        /*
        note that each search here adds to the end, so CD-AB for a backwards search with forwards order.
        We check that we don't fall off the end of the data map at the beginning of time.
        */
        final List<Model> data = new ArrayList<>(maxCount);
        boolean finalSearch = false;
        int searchCount = 0;
        while (data.size() < maxCount && !finalSearch) {
            finalSearch = search.prepare(searchCount++);
            try (TradingInputStream<Model> searchData = search.perform()) {
                searchData.stream().forEach(data::add);
            }
        }
        // sort and remove excess data
        search.sort(data);
        log.debug("Retrieved {} data items in backwards search.  Cleaning to {} items", data.size(), maxCount);
        final int numToRemove = Math.max(0, data.size() - maxCount);
        dataIterator = data.subList(numToRemove, data.size()).iterator();
    }

    /**
     * Return the next element from the materialized results.
     *
     * @return the next element
     * @throws NoSuchElementException if there are no more elements
     */
    @Override
    public Model next() throws NoSuchElementException {
        return dataIterator.next();
    }

    /**
     * Whether another element is available.
     *
     * @return true if another element can be read
     */
    @Override
    public boolean hasNext() {
        return dataIterator.hasNext();
    }

    /**
     * No-op close; there are no underlying resources once results are materialized.
     */
    @Override
    public void close() throws IOException {
        //ignored
    }
}
