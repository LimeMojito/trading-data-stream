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

import com.limemojito.trading.model.stream.TradingInputStreamMapper;

/**
 * Callback invoked for each {@link Tick} as it is read from a {@link com.limemojito.trading.model.TradingInputStream}.
 * <p>
 * Implementations may perform side-effects like metrics, logging, or incremental aggregation. The provided
 * {@link #NO_VISITOR} is a no-op implementation for convenience.
 * </p>
 */
public interface TickVisitor extends TradingInputStreamMapper.Visitor<Tick> {
    TickVisitor NO_VISITOR = (tick) -> {
    };
}
