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

/**
 * Wrapper interface of an integer container for different implementations like OpenBitset, BitSet,
 * ...
 * <p/>
 * @author Peter Karich
 */
public interface GHBitSet
{
    boolean contains( int index );

    void add( int index );

    int getCardinality();

    void clear();

    /**
     * Ensures that the specified index is valid and can be accessed.
     */
    void ensureCapacity( int index );

    /**
     * Searches for a greater or equal entry and returns it.
     * <p/>
     * @return -1 if nothing found
     */
    int next( int index );

    /**
     * @return the specified MyBitSet bs
     */
    GHBitSet copyTo( GHBitSet bs );
}
