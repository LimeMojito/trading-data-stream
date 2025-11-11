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

package com.limemojito.trading.model.tick.dukascopy.cache;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@SuppressWarnings("resource")
@ExtendWith(MockitoExtension.class)
@Slf4j
public class DirectDukascopyNoCacheTest {
    @Mock
    private DirectDukascopyNoCache.DataSource mockUrl;

    @Test
    public void shouldHandleOtherIOExceptions() throws Exception {
        DirectDukascopyNoCache cache = new DirectDukascopyNoCache();
        doThrow(IOException.class).when(mockUrl).openStream();

        assertThat(cache.getRetryCount()).isEqualTo(0);
        assertThatThrownBy(() -> cache.fetchWithRetry(mockUrl, 1)).isInstanceOf(IOException.class);
        assertThat(cache.getRetryCount()).isEqualTo(0);
    }

    @Test
    public void shouldRetryOnRateLimitFailure() throws Exception {
        DirectDukascopyNoCache cache = new DirectDukascopyNoCache();
        doThrow(new IOException(
                "Server returned HTTP response code: 503 for URL: https://datafeed.dukascopy.com/datafeed/EURUSD/2019/05/14/21h_ticks.bi5"))
                .when(mockUrl)
                .openStream();

        assertThat(cache.getRetryCount()).isEqualTo(0);
        assertThatThrownBy(() -> cache.fetchWithRetry(mockUrl, 1)).isInstanceOf(IOException.class);
        assertThat(cache.getRetryCount()).isEqualTo(3);
    }

    @Test
    public void shouldFetchOk() throws Exception {
        DirectDukascopyNoCache cache = new DirectDukascopyNoCache();
        assertThat(cache.getRetryCount()).isEqualTo(0);
        try(InputStream input = cache.stream("EURUSD/2019/05/17/21h_ticks.bi5")){
            log.info("Retrieved OK {}b", IOUtils.toByteArray(input).length);
        }
        assertThat(cache.getRetrieveCount()).isEqualTo(1);
        // well retries are ok here, but we hope they don't happen.
    }
}
