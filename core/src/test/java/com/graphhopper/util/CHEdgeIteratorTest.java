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
package com.graphhopper.util;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.CHGraph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class CHEdgeIteratorTest {
    @Test
    public void testUpdateFlags() {
        CarFlagEncoder carFlagEncoder = new CarFlagEncoder();
        EncodingManager encodingManager = new EncodingManager(carFlagEncoder);
        FastestWeighting weighting = new FastestWeighting(carFlagEncoder);
        EdgeFilter carOutFilter = new DefaultEdgeFilter(carFlagEncoder, false, true);
        GraphHopperStorage ghStorage = new GraphBuilder(encodingManager).setCHGraph(weighting).create();
        CHGraph g = ghStorage.getGraph(CHGraph.class, weighting);
        g.edge(0, 1).setDistance(12).setFlags(carFlagEncoder.setProperties(10, true, true));
        g.edge(0, 2).setDistance(13).setFlags(carFlagEncoder.setProperties(20, true, true));
        ghStorage.freeze();

        assertEquals(2, GHUtility.count(g.getAllEdges()));
        assertEquals(1, GHUtility.count(g.createEdgeExplorer(carOutFilter).setBaseNode(1)));
        EdgeIteratorState iter = GHUtility.getEdge(g, 0, 1);
        assertEquals(1, iter.getAdjNode());
        assertEquals(carFlagEncoder.setProperties(10, true, true), iter.getFlags());

        // update setProperties
        iter.setFlags(carFlagEncoder.setProperties(20, true, false));
        assertEquals(12, iter.getDistance(), 1e-4);

        // update distance
        iter.setDistance(10);
        assertEquals(10, iter.getDistance(), 1e-4);
        assertEquals(0, GHUtility.count(g.createEdgeExplorer(carOutFilter).setBaseNode(1)));
        iter = GHUtility.getEdge(g, 0, 1);
        assertEquals(carFlagEncoder.setProperties(20, true, false), iter.getFlags());
        assertEquals(10, iter.getDistance(), 1e-4);
        assertEquals(1, GHUtility.getNeighbors(g.createEdgeExplorer().setBaseNode(1)).size());
        assertEquals(0, GHUtility.getNeighbors(g.createEdgeExplorer(carOutFilter).setBaseNode(1)).size());
    }
}
