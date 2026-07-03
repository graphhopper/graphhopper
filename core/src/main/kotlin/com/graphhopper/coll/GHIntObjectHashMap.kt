/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.coll

import com.carrotsearch.hppc.HashOrderMixing
import com.carrotsearch.hppc.HashOrderMixingStrategy
import com.carrotsearch.hppc.IntObjectHashMap

/**
 * We often do not mix maps but really need to avoid randomness or that threads can influence each
 * other and so we do not use the default HashOrderMixing employed in HPPC (which does this in a thread-safe manner).
 *
 * @author Peter Karich
 */
class GHIntObjectHashMap<T> @JvmOverloads constructor(
    capacity: Int = 10,
    loadFactor: Double = 0.75,
    hashOrderMixer: HashOrderMixingStrategy = DETERMINISTIC
) : IntObjectHashMap<T>(capacity, loadFactor, hashOrderMixer) {

    companion object {
        @JvmField
        internal val DETERMINISTIC: HashOrderMixingStrategy = HashOrderMixing.constant(123321123321123312L)
    }
}
