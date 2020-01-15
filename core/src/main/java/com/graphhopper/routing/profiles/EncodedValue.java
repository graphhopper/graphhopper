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
package com.graphhopper.routing.profiles;

/**
 * This interface defines how to store and read values from a list of integers
 *
 * @see com.graphhopper.storage.IntsRef
 */
public interface EncodedValue {

    /**
     * This method sets the dataIndex and shift of this EncodedValue object and potentially changes the submitted init
     * object afterwards via calling next
     *
     * @return used bits
     * @see InitializerConfig#next(int)
     */
    int init(InitializerConfig init);

    /**
     * This method returns the hierarchical name like vehicle.type of this EncodedValue
     */
    String getName();

    /**
     * The return value represents the state of this EncodedValue and it can be assumed that two JVMs return the
     * same version when the EncodedValue has the same state unlike the hashCode method. Same version ensures
     * compatibility when reading values.
     */
    int getVersion();

    class InitializerConfig {
        int dataIndex = -1;
        int shift = 32;
        int nextShift = 32;
        int bitMask = 0;

        /**
         * This method determines a space of the specified bits and sets shift and dataIndex accordingly
         *
         * @param usedBits
         */
        void next(int usedBits) {
            shift = nextShift;
            if ((shift - 1 + usedBits) / 32 > (shift - 1) / 32) {
                dataIndex++;
                shift = 0;
            }

            // we need 1L as otherwise it'll fail for usedBits==32
            bitMask = (int) ((1L << usedBits) - 1);
            bitMask <<= shift;
            nextShift = shift + usedBits;
        }

        public int getRequiredBits() {
            return (dataIndex) * 32 + nextShift;
        }
    }
}