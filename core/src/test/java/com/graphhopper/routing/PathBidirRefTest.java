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
package com.graphhopper.routing;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeIterator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class PathBidirRefTest {
    private FlagEncoder carEncoder = new CarFlagEncoder(5, 5, 10);
    private final EncodingManager encodingManager = EncodingManager.create(carEncoder);

    Graph createGraph() {
        return new GraphBuilder(encodingManager).create();
    }

    @Test
    public void testExtract() {
        Graph g = createGraph();
        g.edge(1, 2, 10, true);
        PathBidirRef p = new PathBidirRef(g, new FastestWeighting(carEncoder));
        p.sptEntry = new SPTEntry(0, 2, 0);
        p.sptEntry.parent = new SPTEntry(EdgeIterator.NO_EDGE, 1, 10);
        p.edgeTo = new SPTEntry(EdgeIterator.NO_EDGE, 2, 0);
        p.extract();
        assertEquals(IntArrayList.from(1, 2), p.calcNodes());
        assertEquals(10, p.getDistance(), 1e-4);
    }

    @Test
    public void testExtract2() {
        // 1->2->3
        Graph g = createGraph();
        g.edge(1, 2, 10, false);
        g.edge(2, 3, 20, false);
        // add some turn costs at node 2 where fwd&bwd searches meet. these costs have to be included in the
        // weight and the time of the path
        TurnCostExtension turnCostExtension = (TurnCostExtension) g.getExtension();
        turnCostExtension.addTurnInfo(0, 2, 1, carEncoder.getTurnFlags(false, 5));

        PathBidirRef p = new PathBidirRef(g, new TurnWeighting(new FastestWeighting(carEncoder), turnCostExtension));
        p.sptEntry = new SPTEntry(0, 2, 0.6);
        p.sptEntry.parent = new SPTEntry(EdgeIterator.NO_EDGE, 1, 0);

        p.edgeTo = new SPTEntry(1, 2, 1.2);
        p.edgeTo.parent = new SPTEntry(EdgeIterator.NO_EDGE, 3, 0);
        p.setWeight(5 + 1.8);

        p.extract();
        assertEquals(IntArrayList.from(1, 2, 3), p.calcNodes());
        assertEquals(30, p.getDistance(), 1e-4);
        assertEquals(5 + 1.8, p.getWeight(), 1e-4);
        assertEquals(5000 + 1800, p.getTime(), 1.e-6);
    }
}
