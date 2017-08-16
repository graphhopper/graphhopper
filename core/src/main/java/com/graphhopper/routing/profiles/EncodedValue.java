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
     * @see InitializerConfig#next(int)
     */
    void init(InitializerConfig init);

    String getName();

    class InitializerConfig {
        int dataIndex = 0;
        int shift = 0;
        int propertyIndex = 0;

        /**
         * Returns the necessary bit mask for the current bit range of the specified usedBits
         */
        int next(int usedBits) {
            propertyIndex++;
            int wayBitMask = (1 << usedBits) - 1;
            wayBitMask <<= shift;
            shift += usedBits;
            if (shift > 32) {
                shift = 0;
                dataIndex++;
            }
            return wayBitMask;
        }
    }
}
