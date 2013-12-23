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
 * Default implementation of {@link AbstractTurnCostEncoder} which does not encode any costs yet,
 * but only restrictions in one bit. Therefore, each turn cost encoder requires only 1 bit storage
 * size at the moment.
 * <p>
 * @author karl.huebner
 */
public class DefaultTurnCostEncoder extends AbstractTurnCostEncoder
{
    /**
     * no costs, but only restrictions will be encoded
     */
    public DefaultTurnCostEncoder()
    {
        this(0); //we don't need costs yet
    }

    /**
     * Next to restrictions, turn costs will be encoded as well
     * <p>
     * @param maxCosts the maximum costs to be encoded by this encoder, everything above this costs
     * will be encoded as maxCosts
     */
    public DefaultTurnCostEncoder( int maxCosts )
    {
        super(determineRequiredBits(maxCosts)); //determine the number of bits required to store maxCosts
    }

    private static int determineRequiredBits( int number )
    {
        int bits = 0;
        while (number > 0)
        {
            number = number >> 1;
            bits++;
        }
        return bits;
    }

}
