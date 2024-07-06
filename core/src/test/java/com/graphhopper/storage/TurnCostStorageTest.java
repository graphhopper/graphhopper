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

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static com.graphhopper.util.GHUtility.getEdge;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TurnCostStorageTest {

    private EncodingManager manager;
    private DecimalEncodedValue carTurnCostEnc;
    private DecimalEncodedValue bikeTurnCostEnc;
    private BooleanEncodedValue accessEnc;
    private DecimalEncodedValue speedEnc;

    @BeforeEach
    public void setup() {
        accessEnc = new SimpleBooleanEncodedValue("car_access", true);
        speedEnc = new DecimalEncodedValueImpl("car_speed", 5, 5, false);
        carTurnCostEnc = TurnCost.create("car", 3);
        bikeTurnCostEnc = TurnCost.create("bike", 3);
        manager = EncodingManager.start()
                .add(accessEnc).add(speedEnc)
                .addTurnCostEncodedValue(carTurnCostEnc).addTurnCostEncodedValue(bikeTurnCostEnc).build();
    }

    // 0---1
    // |   /
    // 2--3
    // |
    // 4
    public static void initGraph(BaseGraph g, BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc) {
        GHUtility.setSpeed(60, 60, accessEnc, speedEnc,
                g.edge(0, 1).setDistance(3),
                g.edge(0, 2).setDistance(1),
                g.edge(1, 3).setDistance(1),
                g.edge(2, 3).setDistance(1),
                g.edge(2, 4).setDistance(1));
    }

    /**
     * Test if multiple turn costs can be safely written to the storage and read from it.
     */
    @Test
    public void testMultipleTurnCosts() {
        BaseGraph g = new BaseGraph.Builder(manager).withTurnCosts(true).create();
        initGraph(g, accessEnc, speedEnc);
        TurnCostStorage turnCostStorage = g.getTurnCostStorage();

        DecimalEncodedValue carEnc = carTurnCostEnc;
        DecimalEncodedValue bikeEnc = bikeTurnCostEnc;
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
        turnCostStorage.set(carEnc, edge02, 2, edge23, Double.POSITIVE_INFINITY);
        turnCostStorage.set(bikeEnc, edge02, 2, edge23, Double.POSITIVE_INFINITY);

        turnCostStorage.freeze();

        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(carEnc, edge42, 2, edge23), 0);
        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(bikeEnc, edge42, 2, edge23), 0);

        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(carEnc, edge23, 3, edge31), 0);
        assertEquals(2.0, turnCostStorage.get(bikeEnc, edge23, 3, edge31), 0);

        assertEquals(2.0, turnCostStorage.get(carEnc, edge31, 1, edge10), 0);
        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(bikeEnc, edge31, 1, edge10), 0);

        assertEquals(0.0, turnCostStorage.get(carEnc, edge02, 2, edge24), 0);
        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(bikeEnc, edge02, 2, edge24), 0);

        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(carEnc, edge02, 2, edge23), 0);
        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(bikeEnc, edge02, 2, edge23), 0);

        Set<List<Integer>> turnCosts = new HashSet<>();
        TurnCostStorage.Iterator iterator = turnCostStorage.getAllTurnCosts();
        while (iterator.next()) {
            turnCosts.add(Arrays.asList(iterator.getFromEdge(), iterator.getViaNode(), iterator.getToEdge(),
                    (int) iterator.getCost(carEnc), (int) iterator.getCost(bikeEnc)));
        }

        Set<List<Integer>> expectedTurnCosts = new HashSet<>();
        expectedTurnCosts.add(Arrays.asList(edge31, 1, edge10, 2, Integer.MAX_VALUE));
        expectedTurnCosts.add(Arrays.asList(edge42, 2, edge23, Integer.MAX_VALUE, Integer.MAX_VALUE));
        expectedTurnCosts.add(Arrays.asList(edge02, 2, edge24, 0, Integer.MAX_VALUE));
        expectedTurnCosts.add(Arrays.asList(edge02, 2, edge23, Integer.MAX_VALUE, Integer.MAX_VALUE));
        expectedTurnCosts.add(Arrays.asList(edge23, 3, edge31, Integer.MAX_VALUE, 2));

        assertEquals(expectedTurnCosts, turnCosts);
    }

    @Test
    public void testMergeFlagsBeforeAdding() {
        BaseGraph g = new BaseGraph.Builder(manager).withTurnCosts(true).create();
        initGraph(g, accessEnc, speedEnc);
        TurnCostStorage turnCostStorage = g.getTurnCostStorage();

        DecimalEncodedValue carEnc = carTurnCostEnc;
        DecimalEncodedValue bikeEnc = bikeTurnCostEnc;
        int edge23 = getEdge(g, 2, 3).getEdge();
        int edge02 = getEdge(g, 0, 2).getEdge();

        turnCostStorage.set(carEnc, edge02, 2, edge23, Double.POSITIVE_INFINITY);
        turnCostStorage.set(bikeEnc, edge02, 2, edge23, Double.POSITIVE_INFINITY);
        turnCostStorage.freeze();
        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(carEnc, edge02, 2, edge23), 0);
        assertEquals(Double.POSITIVE_INFINITY, turnCostStorage.get(bikeEnc, edge02, 2, edge23), 0);

        Set<List<Integer>> turnCosts = new HashSet<>();
        TurnCostStorage.Iterator iterator = turnCostStorage.getAllTurnCosts();
        while (iterator.next()) {
            turnCosts.add(Arrays.asList(iterator.getFromEdge(), iterator.getViaNode(), iterator.getToEdge(),
                    (int) iterator.getCost(carEnc), (int) iterator.getCost(bikeEnc)));
        }

        Set<List<Integer>> expectedTurnCosts = new HashSet<>();
        expectedTurnCosts.add(Arrays.asList(edge02, 2, edge23, Integer.MAX_VALUE, Integer.MAX_VALUE));

        assertEquals(expectedTurnCosts, turnCosts);
    }

    @Test
    public void setMultipleTimes() {
        BaseGraph g = new BaseGraph.Builder(manager).withTurnCosts(true).create();
        initGraph(g, accessEnc, speedEnc);
        TurnCostStorage turnCostStorage = g.getTurnCostStorage();
        DecimalEncodedValue carEnc = carTurnCostEnc;
        int edge32 = getEdge(g, 3, 2).getEdge();
        int edge20 = getEdge(g, 2, 0).getEdge();
        turnCostStorage.set(carEnc, edge32, 2, edge20, 0);
        turnCostStorage.set(carEnc, edge32, 2, edge20, Double.POSITIVE_INFINITY);
        turnCostStorage.set(carEnc, edge32, 2, edge20, 0);
        turnCostStorage.freeze();

        assertEquals(0, turnCostStorage.get(carEnc, edge32, 2, edge20));
    }

    @Test
    public void testIterateEmptyStore() {
        BaseGraph g = new BaseGraph.Builder(manager).withTurnCosts(true).create();
        initGraph(g, accessEnc, speedEnc);
        TurnCostStorage turnCostStorage = g.getTurnCostStorage();

        turnCostStorage.freeze();
        TurnCostStorage.Iterator iterator = turnCostStorage.getAllTurnCosts();
        assertFalse(iterator.next());
    }

}
