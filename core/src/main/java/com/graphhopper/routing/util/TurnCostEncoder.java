/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
 * <p>
 * @author karl.huebner
 */
public interface TurnCostEncoder
{
    /**
     * @return true, if, and only if it is encoded in flag
     */
    boolean isRestricted( int flag );

    /**
     * @return the costs in seconds encoded in flag
     */
    int getCosts( int flag );

    /**
     * @return the encoded turn restriction and turn costs
     */
    int flags( boolean restricted, int costs );

    /**
     * sets the shift and index to encode costs/restrictions
     */
    int defineBits( int index, int shift );

    /**
     * whether turn costs nor turn restrictions will be encoded by this encoder, should be used for
     * pedestrians
     */
    static class NoTurnCostsEncoder implements TurnCostEncoder
    {

        @Override
        public boolean isRestricted( int flag )
        {
            return false;
        }

        @Override
        public int getCosts( int flag )
        {
            return 0;
        }

        @Override
        public int flags( boolean restricted, int costs )
        {
            return 0;
        }

        @Override
        public int defineBits( int index, int shift )
        {
            return shift;
        }

    }

}
