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
package com.graphhopper.coll;

import java.util.BitSet;

/**
 * @author Peter Karich
 */
public class GHBitSetImpl extends BitSet implements GHBitSet
{
    public GHBitSetImpl()
    {
    }

    public GHBitSetImpl( int nbits )
    {
        super(nbits);
    }

    @Override
    public boolean contains( int index )
    {
        return super.get(index);
    }

    @Override
    public void add( int index )
    {
        super.set(index);
    }

    @Override
    public int getCardinality()
    {
        return super.cardinality();
    }

    @Override
    public void ensureCapacity( int size )
    {
    }

    @Override
    public int next( int index )
    {
        return super.nextSetBit(index);
    }

    public int nextClear( int index )
    {
        return super.nextClearBit(index);
    }

    @Override
    public GHBitSet copyTo( GHBitSet bs )
    {
        bs.clear();
        if (bs instanceof GHBitSetImpl)
        {
            ((GHBitSetImpl) bs).or(this);
        } else
        {
            int len = size();
            bs.ensureCapacity(len);
            for (int index = super.nextSetBit(0); index >= 0;
                    index = super.nextSetBit(index + 1))
            {
                bs.add(index);
            }
        }
        return bs;
    }
}
