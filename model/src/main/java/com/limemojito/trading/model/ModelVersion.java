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

/**
 * Declares the semantic version of the trading data stream model.
 * <p>
 * Use this version string in serialized payloads and clients to ensure compatibility between
 * producers and consumers of the model types found in this package.
 */
public interface ModelVersion {
    /**
     * Current version of the model contract exposed by classes in {@code com.limemojito.trading.model}.
     */
    String VERSION = "1.0";
}
