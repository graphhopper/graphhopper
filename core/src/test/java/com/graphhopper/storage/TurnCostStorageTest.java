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

import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.TurnCost;
import com.graphhopper.routing.util.BikeFlagEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.graphhopper.util.GHUtility.getEdge;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TurnCostStorageTest {

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
        TurnCostStorage turnCostStorage = g.getTurnCostStorage();

        DecimalEncodedValue carEnc = manager.getDecimalEncodedValue(TurnCost.key("car"));
        DecimalEncodedValue bikeEnc = manager.getDecimalEncodedValue(TurnCost.key("bike"));
        int edge42 = getEdge(g, 4, 2).getEdge();
        int edge23 = getEdge(g, 2, 3).getEdge();
        int edge31 = getEdge(g, 3, 1).getEdge();
        int edge10 = getEdge(g, 1, 0).getEdge();
        int edge02 = getEdge(g, 0, 2).getEdge();
        int edge24 = getEdge(g, 2, 4).getEdge();

        turnCostStorage.set(carEnc, edge42, 2, edge23, Double.POSITIVE_INFINITY);
        turnCostStorage.set(bikeEnc, edge42, 2, edge23, Double.POSITIVE_INFINITY);
        turnCostStorage.set(carEnc, edge23, 3, edge31, Double.POSITIVE_INFINITY);
        turnCostStorage.set(bikeEnc, edge23, 3, edge31, 2.0);
        turnCostStorage.set(carEnc, edge31, 1, edge10, 2.0);
        turnCostStorage.set(bikeEnc, edge31, 1, edge10, Double.POSITIVE_INFINITY);
        turnCostStorage.set(bikeEnc, edge02, 2, edge24, Double.POSITIVE_INFINITY);

        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(carEnc, edge42, 2, edge23), 0);
        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(bikeEnc, edge42, 2, edge23), 0);

        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(carEnc, edge23, 3, edge31), 0);
        assertEquals(2.0, turnCostStorage.get(bikeEnc, edge23, 3, edge31), 0);

        assertEquals(2.0, turnCostStorage.get(carEnc, edge31, 1, edge10), 0);
        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(bikeEnc, edge31, 1, edge10), 0);

        assertEquals(0.0, turnCostStorage.get(carEnc, edge02, 2, edge24), 0);
        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(bikeEnc, edge02, 2, edge24), 0);

        turnCostStorage.set(carEnc, edge02, 2, edge23, Double.POSITIVE_INFINITY);
        turnCostStorage.set(bikeEnc, edge02, 2, edge23, Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(carEnc, edge02, 2, edge23), 0);
        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(bikeEnc, edge02, 2, edge23), 0);

        Set<List<Integer>> allTurnRelations = new HashSet<>();
        TurnCostStorage.TurnRelationIterator iterator = turnCostStorage.getAllTurnRelations();
        while (iterator.next()) {
            allTurnRelations.add(Arrays.asList(iterator.getFromEdge(), iterator.getViaNode(), iterator.getToEdge(),
                    (int) iterator.getCost(carEnc), (int) iterator.getCost(bikeEnc)));
        }

        Set<List<Integer>> expectedTurnRelations = new HashSet<>();
        expectedTurnRelations.add(Arrays.asList(edge31, 1, edge10, 2, Integer.MAX_VALUE));
        expectedTurnRelations.add(Arrays.asList(edge42, 2, edge23, Integer.MAX_VALUE, Integer.MAX_VALUE));
        expectedTurnRelations.add(Arrays.asList(edge02, 2, edge24, 0, Integer.MAX_VALUE));
        expectedTurnRelations.add(Arrays.asList(edge02, 2, edge23, Integer.MAX_VALUE, Integer.MAX_VALUE));
        expectedTurnRelations.add(Arrays.asList(edge23, 3, edge31, Integer.MAX_VALUE, 2));

        assertEquals(expectedTurnRelations, allTurnRelations);
    }

    @Test
    public void testMergeFlagsBeforeAdding() {
        GraphHopperStorage g = new GraphBuilder(manager).create();
        initGraph(g);
        TurnCostStorage turnCostStorage = g.getTurnCostStorage();

        DecimalEncodedValue carEnc = manager.getDecimalEncodedValue(TurnCost.key("car"));
        DecimalEncodedValue bikeEnc = manager.getDecimalEncodedValue(TurnCost.key("bike"));
        int edge23 = getEdge(g, 2, 3).getEdge();
        int edge02 = getEdge(g, 0, 2).getEdge();

        turnCostStorage.set(carEnc, edge02, 2, edge23, Double.POSITIVE_INFINITY);
        turnCostStorage.set(bikeEnc, edge02, 2, edge23, Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(carEnc, edge02, 2, edge23), 0);
        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(bikeEnc, edge02, 2, edge23), 0);

        Set<List<Integer>> allTurnRelations = new HashSet<>();
        TurnCostStorage.TurnRelationIterator iterator = turnCostStorage.getAllTurnRelations();
        while (iterator.next()) {
            allTurnRelations.add(Arrays.asList(iterator.getFromEdge(), iterator.getViaNode(), iterator.getToEdge(),
                    (int) iterator.getCost(carEnc), (int) iterator.getCost(bikeEnc)));
        }

        Set<List<Integer>> expectedTurnRelations = new HashSet<>();
        expectedTurnRelations.add(Arrays.asList(edge02, 2, edge23, Integer.MAX_VALUE, Integer.MAX_VALUE));

        assertEquals(expectedTurnRelations, allTurnRelations);
    }

    @Test
    public void testIterateEmptyStore() {
        GraphHopperStorage g = new GraphBuilder(manager).create();
        initGraph(g);
        TurnCostStorage turnCostStorage = g.getTurnCostStorage();

        TurnCostStorage.TurnRelationIterator iterator = turnCostStorage.getAllTurnRelations();
        assertFalse(iterator.next());
    }

}
