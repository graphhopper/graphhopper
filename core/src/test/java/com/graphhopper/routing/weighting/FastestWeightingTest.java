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
package com.graphhopper.routing.weighting;

import com.graphhopper.routing.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.Bike2WeightFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.Parameters.Routing;
import org.junit.Test;

import static com.graphhopper.util.GHUtility.createMockedEdgeIteratorState;
import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class FastestWeightingTest {
    private final FlagEncoder encoder = new EncodingManager("car").getEncoder("car");

    @Test
    public void testMinWeightHasSameUnitAs_getWeight() {
        Weighting instance = new FastestWeighting(encoder);
        long flags = encoder.setProperties(encoder.getMaxSpeed(), true, true);
        assertEquals(instance.getMinWeight(10), instance.calcWeight(createMockedEdgeIteratorState(10, flags), false, EdgeIterator.NO_EDGE), 1e-8);
    }

    @Test
    public void testWeightWrongHeading() {
        Weighting instance = new FastestWeighting(encoder, new PMap().
                put(Parameters.Routing.HEADING_PENALTY, "100"));
        VirtualEdgeIteratorState virtEdge = new VirtualEdgeIteratorState(0, 1, 1, 2, 10,
                encoder.setProperties(10, true, true), "test", Helper.createPointList(51, 0, 51, 1));
        double time = instance.calcWeight(virtEdge, false, 0);

        virtEdge.setUnfavored(true);
        // heading penalty on edge
        assertEquals(time + 100, instance.calcWeight(virtEdge, false, 0), 1e-8);
        // only after setting it
        virtEdge.setUnfavored(true);
        assertEquals(time + 100, instance.calcWeight(virtEdge, true, 0), 1e-8);
        // but not after releasing it
        virtEdge.setUnfavored(false);
        assertEquals(time, instance.calcWeight(virtEdge, true, 0), 1e-8);

        // test default penalty
        virtEdge.setUnfavored(true);
        instance = new FastestWeighting(encoder);
        assertEquals(time + Routing.DEFAULT_HEADING_PENALTY, instance.calcWeight(virtEdge, false, 0), 1e-8);
    }

    @Test
    public void testSpeed0() {
        Weighting instance = new FastestWeighting(encoder);

        assertEquals(1.0 / 0, instance.calcWeight(createMockedEdgeIteratorState(10, encoder.setProperties(0, true, true)), false, EdgeIterator.NO_EDGE), 1e-8);

        // 0 / 0 returns NaN but calcWeight should not return NaN!
        assertEquals(1.0 / 0, instance.calcWeight(createMockedEdgeIteratorState(0, encoder.setProperties(0, true, true)), false, EdgeIterator.NO_EDGE), 1e-8);
    }

    @Test
    public void testTime() {
        FlagEncoder tmpEnc = new Bike2WeightFlagEncoder();
        GraphHopperStorage g = new GraphBuilder(new EncodingManager(tmpEnc)).create();
        Weighting w = new FastestWeighting(tmpEnc);

        long flags = tmpEnc.setSpeed(tmpEnc.setReverseSpeed(tmpEnc.setAccess(0, true, true), 10), 15);
        EdgeIteratorState edge = GHUtility.createMockedEdgeIteratorState(100000, flags);

        assertEquals(375 * 60 * 1000, w.calcMillis(edge, false, EdgeIterator.NO_EDGE));
        assertEquals(600 * 60 * 1000, w.calcMillis(edge, true, EdgeIterator.NO_EDGE));

        g.close();
    }
}
