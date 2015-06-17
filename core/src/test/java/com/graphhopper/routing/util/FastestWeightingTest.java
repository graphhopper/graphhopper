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

import com.graphhopper.routing.VirtualEdgeIteratorState;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class FastestWeightingTest
{
    private final FlagEncoder encoder = new EncodingManager("CAR").getEncoder("CAR");

    @Test
    public void testMinWeightHasSameUnitAs_getWeight()
    {
        FastestWeighting instance = new FastestWeighting(encoder);
        long flags = encoder.setProperties(encoder.getMaxSpeed(), true, true);
        assertEquals(instance.getMinWeight(10), instance.calcWeight(createEdge(10, flags), false, EdgeIterator.NO_EDGE), 1e-8);
    }

    @Test
    public void testWeightWrongHeading()
    {
        FastestWeighting instance = new FastestWeighting(encoder, new PMap().put("heading_penalty", "100"));

        VirtualEdgeIteratorState virtEdge = new VirtualEdgeIteratorState(0, 1, 1, 2, 10,
                encoder.setProperties(10, true, true), "test", Helper.createPointList(51, 0, 51, 1));
        double time = instance.calcWeight(virtEdge, false, 0);

        virtEdge.setVirtualEdgePreference(true, false);
        // heading penalty on edge
        assertEquals(time + 100, instance.calcWeight(virtEdge, false, 0), 1e-8);
        // but not in reverse heading
        assertEquals(time, instance.calcWeight(virtEdge, true, 0), 1e-8);
        // only after setting it
        virtEdge.setVirtualEdgePreference(true, true);
        assertEquals(time + 100, instance.calcWeight(virtEdge, true, 0), 1e-8);
        // but not after releasing it
        virtEdge.setVirtualEdgePreference(false, true);
        assertEquals(time, instance.calcWeight(virtEdge, true, 0), 1e-8);

        // test default penalty
        instance = new FastestWeighting(encoder);
        assertEquals(time + FastestWeighting.DEFAULT_HEADING_PENALTY, instance.calcWeight(virtEdge, false, 0), 1e-8);
    }

    @Test
    public void testSpeed0()
    {
        FastestWeighting instance = new FastestWeighting(encoder);

        assertEquals(1.0 / 0, instance.calcWeight(createEdge(10, encoder.setProperties(0, true, true)), false, EdgeIterator.NO_EDGE), 1e-8);

        // 0 / 0 returns NaN but calcWeight should not return NaN!
        assertEquals(1.0 / 0, instance.calcWeight(createEdge(0, encoder.setProperties(0, true, true)), false, EdgeIterator.NO_EDGE), 1e-8);
    }

    EdgeIterator createEdge( final double distance, final long flags )
    {
        return new GHUtility.DisabledEdgeIterator()
        {
            @Override
            public double getDistance()
            {
                return distance;
            }

            @Override
            public long getFlags()
            {
                return flags;
            }

            @Override
            public boolean getBoolean( int key, boolean reverse, boolean _default )
            {
                return _default;
            }
        };
    }
}
