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
package com.graphhopper.routing.lm;

import com.graphhopper.routing.RoutingAlgorithmTest;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks;
import com.graphhopper.routing.util.AreaIndex;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.weighting.custom.CustomModelParser;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class LandmarkStorageTest {
    private BaseGraph graph;
    private BooleanEncodedValue subnetworkEnc;
    private EncodingManager encodingManager;
    private BooleanEncodedValue accessEnc;
    private DecimalEncodedValue speedEnc;

    @BeforeEach
    public void setUp() {
        subnetworkEnc = Subnetwork.create("car");
        accessEnc = new SimpleBooleanEncodedValue("access", true);
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, false);
        encodingManager = new EncodingManager.Builder().add(accessEnc).add(speedEnc).add(subnetworkEnc).build();
        graph = new BaseGraph.Builder(encodingManager).create();
    }

    @AfterEach
    public void tearDown() {
        if (graph != null)
            graph.close();
    }

    @Test
    public void testInfiniteWeight() {
        Directory dir = new RAMDirectory();
        graph.edge(0, 1);
        LandmarkStorage lms = new LandmarkStorage(graph, encodingManager, dir, new LMConfig("car", new FastestWeighting(accessEnc, speedEnc)), 8).
                setMaximumWeight(LandmarkStorage.PRECISION);
        lms.createLandmarks();

        // default is infinity but return short max
        assertTrue(lms.isInfinity(0));
        assertEquals(LandmarkStorage.SHORT_MAX, lms.getFromWeight(0, 0));

        // store max directly
        lms.setWeight(0, LandmarkStorage.SHORT_MAX);
        assertFalse(lms.isInfinity(0));
        assertEquals(LandmarkStorage.SHORT_MAX, lms.getFromWeight(0, 0));

        // store only max even if weight is larger
        lms.setWeight(0, Integer.MAX_VALUE);
        assertFalse(lms.isInfinity(0));
        assertEquals(LandmarkStorage.SHORT_MAX, lms.getFromWeight(0, 0));

        // If bigger than integer max throw exception. Could this happen if weights add up too much?
        assertThrows(UnsupportedOperationException.class, () -> lms.setWeight(0, (double) Integer.MAX_VALUE + 1));
    }

    @Test
    public void testSetGetWeight() {
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(40.1));
        Directory dir = new RAMDirectory();
        LandmarkStorage lms = new LandmarkStorage(graph, encodingManager, dir,
                new LMConfig("c1", CustomModelParser.createFastestWeighting(accessEnc, speedEnc, encodingManager)), 4).
                setMaximumWeight(LandmarkStorage.PRECISION);
        lms._getInternalDA().create(2000);
        // 2^16=65536, use -1 for infinity and -2 for maximum
        lms.setWeight(0, 65536);
        // reached maximum value but do not reset to 0 instead use 2^16-2
        assertEquals(65536 - 2, lms.getFromWeight(0, 0));
        lms.setWeight(0, 65535);
        assertEquals(65534, lms.getFromWeight(0, 0));
        lms.setWeight(0, 79999);
        assertEquals(65534, lms.getFromWeight(0, 0));
    }

    @Test
    public void testWithSubnetworks() {
        // 0-1-2..4-5->6
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(10.1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(10.2));

        graph.edge(2, 4).set(accessEnc, false, false);
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 5).setDistance(10.5));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(5, 6).setDistance(10.6));

        Weighting weighting = CustomModelParser.createFastestWeighting(accessEnc, speedEnc, encodingManager);
        // 1 means => 2 allowed edge keys => excludes the node 6
        subnetworkRemoval(weighting, 1);

        LandmarkStorage storage = new LandmarkStorage(graph, encodingManager, new RAMDirectory(), new LMConfig("car", weighting), 2);
        storage.setMinimumNodes(2);
        storage.createLandmarks();
        assertEquals(3, storage.getSubnetworksWithLandmarks());
        assertEquals("[2, 0]", Arrays.toString(storage.getLandmarks(1)));
        // do not include 6 as landmark!
        assertEquals("[5, 4]", Arrays.toString(storage.getLandmarks(2)));
    }

    @Test
    public void testWithStronglyConnectedComponent() {
        // 0 - 1 - 2 = 3 - 4
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(10.1));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(10.2));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 3).setDistance(10.3));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(3, 2).setDistance(10.2));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(3, 4).setDistance(10.4));

        Weighting weighting = CustomModelParser.createFastestWeighting(accessEnc, speedEnc, encodingManager);

        // 3 nodes => 6 allowed edge keys but still do not exclude 3 & 4 as strongly connected and not a too small subnetwork!
        subnetworkRemoval(weighting, 4);

        LandmarkStorage storage = new LandmarkStorage(graph, encodingManager, new RAMDirectory(), new LMConfig("car", weighting), 2);
        storage.setMinimumNodes(3);
        storage.createLandmarks();
        assertEquals(2, storage.getSubnetworksWithLandmarks());
        assertEquals("[4, 0]", Arrays.toString(storage.getLandmarks(1)));
    }

    private void subnetworkRemoval(Weighting weighting, int minNodeSize) {
        // currently we rely on subnetwork removal in Landmark preparation, see #2256
        // PrepareRoutingSubnetworks removes OSM bugs regarding turn restriction mapping which the node-based Tarjan in Landmark preparation can't
        new PrepareRoutingSubnetworks(graph, Collections.singletonList(new PrepareRoutingSubnetworks.PrepareJob(subnetworkEnc, weighting))).
                setMinNetworkSize(minNodeSize).
                doWork();
    }

    @Test
    public void testWithOnewaySubnetworks() {
        // 0 -- 1 -> 2 -> 3
        // 4 -- 5 ->/
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(10.1));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(1, 2).setDistance(10.2));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(2, 3).setDistance(10.3));

        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(4, 5).setDistance(10.5));
        GHUtility.setSpeed(60, true, false, accessEnc, speedEnc, graph.edge(5, 2).setDistance(10.2));

        Weighting weighting = CustomModelParser.createFastestWeighting(accessEnc, speedEnc, encodingManager);
        // 1 allowed node => 2 allowed edge keys (exclude 2 and 3 because they are separate too small oneway subnetworks)
        subnetworkRemoval(weighting, 1);

        LandmarkStorage storage = new LandmarkStorage(graph, encodingManager, new RAMDirectory(), new LMConfig("car", weighting), 2);
        storage.setMinimumNodes(2);
        storage.createLandmarks();

        assertEquals(3, storage.getSubnetworksWithLandmarks());
        assertEquals("[1, 0]", Arrays.toString(storage.getLandmarks(1)));
        assertEquals("[5, 4]", Arrays.toString(storage.getLandmarks(2)));
    }

    @Test
    public void testWeightingConsistence1() {
        // create an indifferent problem: shortest weighting can pass the speed==0 edge but fastest cannot (?)
        graph.edge(0, 1).setDistance(10.1).set(accessEnc, true, true);
        GHUtility.setSpeed(30, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(10));
        graph.edge(2, 3).setDistance(10.1).set(accessEnc, true, true);

        LandmarkStorage storage = new LandmarkStorage(graph, encodingManager, new RAMDirectory(),
                new LMConfig("car", CustomModelParser.createFastestWeighting(accessEnc, speedEnc, encodingManager)), 2);
        storage.setMinimumNodes(2);
        storage.createLandmarks();

        assertEquals(2, storage.getSubnetworksWithLandmarks());
        assertEquals("[2, 1]", Arrays.toString(storage.getLandmarks(1)));
    }

    @Test
    public void testWeightingConsistence2() {
        GHUtility.setSpeed(30, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(10));
        graph.edge(2, 3).setDistance(10.1).set(accessEnc, true, true);
        GHUtility.setSpeed(30, true, true, accessEnc, speedEnc, graph.edge(2, 3).setDistance(10));

        LandmarkStorage storage = new LandmarkStorage(graph, encodingManager, new RAMDirectory(),
                new LMConfig("car", CustomModelParser.createFastestWeighting(accessEnc, speedEnc, encodingManager)), 2);
        storage.setMinimumNodes(2);
        storage.createLandmarks();

        assertEquals(3, storage.getSubnetworksWithLandmarks());
        assertEquals("[1, 0]", Arrays.toString(storage.getLandmarks(1)));
        assertEquals("[3, 2]", Arrays.toString(storage.getLandmarks(2)));
    }

    @Test
    public void testWithBorderBlocking() {
        RoutingAlgorithmTest.initBiGraph(graph, accessEnc, speedEnc);

        LandmarkStorage storage = new LandmarkStorage(graph, encodingManager, new RAMDirectory(),
                new LMConfig("car", CustomModelParser.createFastestWeighting(accessEnc, speedEnc, encodingManager)), 2);
        final SplitArea right = new SplitArea(emptyList());
        final SplitArea left = new SplitArea(emptyList());
        final AreaIndex<SplitArea> areaIndex = new AreaIndex<SplitArea>(emptyList()) {
            @Override
            public List<SplitArea> query(double lat, double lon) {
                if (lon > 0.00105) {
                    return Collections.singletonList(right);
                } else {
                    return Collections.singletonList(left);
                }
            }
        };
        storage.setAreaIndex(areaIndex);
        storage.setMinimumNodes(2);
        storage.createLandmarks();
        assertEquals(3, storage.getSubnetworksWithLandmarks());
    }
}
