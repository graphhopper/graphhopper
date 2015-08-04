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

import com.graphhopper.util.EdgeIteratorState;

/**
 * @author Peter Karich
 */
public class DefaultEdgeFilter implements EdgeFilter
{
    private final boolean in;
    private final boolean out;
    private FlagEncoder encoder;

    /**
     * Creates an edges filter which accepts both direction of the specified vehicle.
     */
    public DefaultEdgeFilter( FlagEncoder encoder )
    {
        this(encoder, true, true);
    }

    public DefaultEdgeFilter( FlagEncoder encoder, boolean in, boolean out )
    {
        this.encoder = encoder;
        this.in = in;
        this.out = out;
    }

    @Override
    public final boolean accept( EdgeIteratorState iter )
    {
        long flags = iter.getFlags();
        return out && encoder.isForward(flags) || in && encoder.isBackward(flags);
    }

    @Override
    public String toString()
    {
        return encoder.toString() + ", in:" + in + ", out:" + out;
    }
}
