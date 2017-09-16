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
 * This class defines how to store and read values from an edge.
 */
public interface EncodedValue {

    /**
     * This method sets the dataIndex and shift of this EncodedValue object and potentially changes the submitted init
     * object afterwards via calling next
     *
     * @see InitializerConfig#find(int)
     */
    void init(InitializerConfig init, int maxBytes);

    String getName();

    class InitializerConfig {
        int dataIndex = -1;
        int shift = 32;
        int nextShift = 32;
        int propertyIndex = 0;
        int wayBitMask = 0;

        void find(int usedBits) {
            shift = nextShift;
            propertyIndex++;
            if ((shift - 1 + usedBits) / 32 > (shift - 1) / 32) {
                dataIndex++;
                shift = 0;
            }

            // we need 1L as otherwise it'll fail for usedBits==32
            wayBitMask = (int) ((1L << usedBits) - 1);
            wayBitMask <<= shift;
            nextShift = shift + usedBits;
        }
    }
}