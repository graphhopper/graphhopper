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

package com.graphhopper.storage;

import com.graphhopper.routing.profiles.TurnCost;
import com.graphhopper.routing.util.BikeFlagEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import org.junit.Before;
import org.junit.Test;

import static com.graphhopper.util.GHUtility.getEdge;
import static org.junit.Assert.assertEquals;

public class TurnCostStorageTest {

    private final IntsRef EMPTY = TurnCost.createFlags();
    private EncodingManager manager;

    @Before
    public void setup() {
        FlagEncoder carEncoder = new CarFlagEncoder(5, 5, 3);
        FlagEncoder bikeEncoder = new BikeFlagEncoder(5, 5, 3);
        manager = EncodingManager.create(carEncoder, bikeEncoder);
    }

    // 0---1
    // |   /
    // 2--3
    // |
    // 4
    public static void initGraph(Graph g) {
        g.edge(0, 1, 3, true);
        g.edge(0, 2, 1, true);
        g.edge(1, 3, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(2, 4, 1, true);
    }

    /**
     * Test if multiple turn costs can be safely written to the storage and read from it.
     */
    @Test
    public void testMultipleTurnCosts() {
        GraphHopperStorage g = new GraphBuilder(manager).create();
        initGraph(g);

        int edge42 = getEdge(g, 4, 2).getEdge();
        int edge23 = getEdge(g, 2, 3).getEdge();
        int edge31 = getEdge(g, 3, 1).getEdge();
        int edge10 = getEdge(g, 1, 0).getEdge();
        int edge02 = getEdge(g, 0, 2).getEdge();
        int edge24 = getEdge(g, 2, 4).getEdge();

        setTurnCost(g, edge42, edge23, "car", 2, Double.POSITIVE_INFINITY);
        setTurnCost(g, edge42, edge23, "bike", 2, Double.POSITIVE_INFINITY);
        setTurnCost(g, edge23, edge31, "car", 3, Double.POSITIVE_INFINITY);
        setTurnCost(g, edge23, edge31, "bike", 3, 2.0);
        setTurnCost(g, edge31, edge10, "car", 1, 2.0);
        setTurnCost(g, edge31, edge10, "bike", 1, Double.POSITIVE_INFINITY);
        g.getTurnCostStorage().setOrMerge(EMPTY, edge02, 2, edge24, false);
        setTurnCost(g, edge02, edge24, "bike", 2, Double.POSITIVE_INFINITY);

        assertEquals(Double.POSITIVE_INFINITY, getTurnCost(g, edge42, edge23, "car", 2), 0);
        assertEquals(Double.POSITIVE_INFINITY, getTurnCost(g, edge42, edge23, "bike", 2), 0);

        assertEquals(Double.POSITIVE_INFINITY, getTurnCost(g, edge23, edge31, "car", 3), 0);
        assertEquals(2.0, getTurnCost(g, edge23, edge31, "bike", 3), 0);

        assertEquals(2.0, getTurnCost(g, edge31, edge10, "car", 1), 0);
        assertEquals(Double.POSITIVE_INFINITY, getTurnCost(g, edge31, edge10, "bike", 1), 0);

        assertEquals(0.0, getTurnCost(g, edge02, edge24, "car", 2), 0);
        assertEquals(Double.POSITIVE_INFINITY, getTurnCost(g, edge02, edge24, "bike", 2), 0);

        // merge per default
        setTurnCost(g, edge02, edge23, "car", 2, Double.POSITIVE_INFINITY);
        setTurnCost(g, edge02, edge23, "bike", 2, Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, getTurnCost(g, edge02, edge23, "car", 2), 0);
        assertEquals(Double.POSITIVE_INFINITY, getTurnCost(g, edge02, edge23, "bike", 2), 0);

        // overwrite unrelated turn cost value
        g.getTurnCostStorage().setOrMerge(EMPTY, edge02, 2, edge23, false);
        g.getTurnCostStorage().setOrMerge(EMPTY, edge02, 2, edge23, false);
        setTurnCost(g, edge02, edge23, "bike", 2, Double.POSITIVE_INFINITY);
        assertEquals(0, getTurnCost(g, edge02, edge23, "car", 2), 0);
        assertEquals(Double.POSITIVE_INFINITY, getTurnCost(g, edge02, edge23, "bike", 2), 0);

        // clear
        g.getTurnCostStorage().setOrMerge(EMPTY, edge02, 2, edge23, false);
        assertEquals(0, getTurnCost(g, edge02, edge23, "car", 2), 0);
        assertEquals(0, getTurnCost(g, edge02, edge23, "bike", 2), 0);
    }

    @Test
    public void testMergeFlagsBeforeAdding() {
        GraphHopperStorage g = new GraphBuilder(manager).create();
        initGraph(g);

        int edge23 = getEdge(g, 2, 3).getEdge();
        int edge02 = getEdge(g, 0, 2).getEdge();
        setTurnCost(g, edge02, edge23, "car", 2, Double.POSITIVE_INFINITY);
        setTurnCost(g, edge02, edge23, "bike", 2, Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, getTurnCost(g, edge02, edge23, "car", 2), 0);
        assertEquals(Double.POSITIVE_INFINITY, getTurnCost(g, edge02, edge23, "bike", 2), 0);
    }

    private void setTurnCost(GraphHopperStorage g, int fromEdge, int toEdge, String vehicle, int viaNode, double cost) {
        g.getTurnCostStorage().setExpensive(vehicle, manager, fromEdge, viaNode, toEdge, cost);
    }

    private double getTurnCost(GraphHopperStorage g, int fromEdge, int toEdge, String vehicle, int viaNode) {
        return g.getTurnCostStorage().getExpensive(vehicle, manager, fromEdge, viaNode, toEdge);
    }
}
