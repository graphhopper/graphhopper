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
 * Very simple turn cost encoder, which stores the turn restriction in the first bit, and the turn
 * costs (in seconds) in x additional bits (0..2^x-1)
 * <p>
 * @author karl.huebner
 */
public abstract class AbstractTurnCostEncoder implements TurnCostEncoder
{
    private final int maxCostsBits;
    private final int costsMask;

    protected int restrictionBit;
    protected int costShift;

    public AbstractTurnCostEncoder( int maxCostsBits )
    {
        this.maxCostsBits = maxCostsBits;

        int mask = 0;
        for (int i = 0; i < this.maxCostsBits; i++)
        {
            mask |= (1 << i);
        }
        this.costsMask = mask;

        defineBits(0, 0);
    }

    @Override
    public int defineBits( int index, int shift )
    {
        restrictionBit = 1 << shift;
        costShift = shift + 1;
        return shift + maxCostsBits + 1;
    }

    @Override
    public boolean isRestricted( int flag )
    {
        return (flag & restrictionBit) != 0;
    }

    @Override
    public int getCosts( int flag )
    {
        int result = (flag >> costShift) & costsMask;
        if (result >= Math.pow(2, maxCostsBits) || result < 0)
        {
            throw new IllegalStateException("Wrong encoding of turn costs");
        }
        return result;
    }

    @Override
    public int flags( boolean restricted, int costs )
    {
        costs = Math.min(costs, (int) (Math.pow(2, maxCostsBits) - 1));
        int encode = costs << costShift;
        if (restricted)
        {
            encode |= restrictionBit;
        }
        return encode;
    }

}
