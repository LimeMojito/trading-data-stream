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

package com.limemojito.trading.model.bar;

import com.limemojito.trading.model.stream.TradingInputStreamMapper;

/**
 * Callback invoked for each {@link Bar} as it is produced or streamed.
 * Implementations can perform side-effects such as persistence, logging, or aggregation.
 */
public interface BarVisitor extends TradingInputStreamMapper.Visitor<Bar> {
    /**
     * No-op visitor that performs no action when invoked.
     */
    BarVisitor NO_VISITOR = (bar) -> {
    };
}
