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
package com.graphhopper.routing.util;

/**
 * Encodes and decodes a turn restriction and turn costs within a integer flag
 *
 * @author Karl Hübner
 */
public interface TurnCostEncoder {
    /**
     * @return true, if the turn restriction is encoded in the specified flags
     */
    boolean isTurnRestricted(long flags);

    /**
     * @return the costs encoded in the specified flag, if restricted it will be
     * Double.POSITIVE_INFINITY
     */
    double getTurnCost(long flags);

    /**
     * @param restricted true if restricted turn, equivalent to specifying of costs
     *                   Double.POSITIVE_INFINITY
     * @param costs      the turn costs, specify 0 or Double.POSITIVE_INFINITY if restricted == true.
     *                   Only used if restricted == false.
     * @return the encoded flags
     */
    long getTurnFlags(boolean restricted, double costs);

    /**
     * No turn costs will be enabled by this encoder, should be used for pedestrians
     */
    class NoTurnCostsEncoder implements TurnCostEncoder {

        @Override
        public boolean isTurnRestricted(long flags) {
            return false;
        }

        @Override
        public double getTurnCost(long flags) {
            return 0;
        }

        @Override
        public long getTurnFlags(boolean restriction, double costs) {
            return 0;
        }
    }
}
