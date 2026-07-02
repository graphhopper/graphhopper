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
package com.graphhopper.routing.ev

import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * This interface defines how to store and read values from a list of integers
 *
 * @see com.graphhopper.storage.IntsRef
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "className")
interface EncodedValue {

    /**
     * This method sets the dataIndex and shift of this EncodedValue object and potentially changes the submitted init
     * object afterwards via calling next
     *
     * @return used bits
     * @see InitializerConfig.next
     */
    fun init(init: InitializerConfig): Int

    /**
     * This method returns the hierarchical name like vehicle.type of this EncodedValue
     */
    val name: String

    /**
     * @return true if this EncodedValue can store a different value for its reverse direction
     */
    val isStoreTwoDirections: Boolean

    class InitializerConfig {
        @JvmField
        internal var dataIndex = -1

        @JvmField
        internal var shift = 32

        @JvmField
        internal var nextShift = 32

        @JvmField
        internal var bitMask = 0

        /**
         * This method determines a space of the specified bits and sets shift and dataIndex accordingly
         */
        internal fun next(usedBits: Int) {
            shift = nextShift
            if ((shift - 1 + usedBits) / 32 > (shift - 1) / 32) {
                dataIndex++
                shift = 0
            }

            // we need 1L as otherwise it'll fail for usedBits==32
            bitMask = ((1L shl usedBits) - 1).toInt()
            bitMask = bitMask shl shift
            nextShift = shift + usedBits
        }

        private val requiredBits: Int
            get() = dataIndex * 32 + nextShift

        val requiredInts: Int
            get() = Math.ceil(requiredBits / 32.0).toInt()

        val requiredBytes: Int
            get() = Math.ceil(requiredBits / 8.0).toInt()
    }
}
