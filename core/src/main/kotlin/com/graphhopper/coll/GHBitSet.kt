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

/**
 * Wrapper interface of an integer container for different implementations like OpenBitset, BitSet,
 * ...
 *
 * Loop via
 * `for (int i = set.next(0); i >= 0; i = set.next(i + 1)) {...}`
 *
 * @author Peter Karich
 */
interface GHBitSet {
    fun contains(index: Int): Boolean

    fun add(index: Int)

    fun remove(index: Int)

    val cardinality: Int

    fun clear()

    /**
     * Searches for a greater or equal entry and returns it.
     *
     * @return -1 if nothing found
     */
    fun next(index: Int): Int

    /**
     * @return the specified MyBitSet bs
     */
    fun copyTo(bs: GHBitSet): GHBitSet
}
