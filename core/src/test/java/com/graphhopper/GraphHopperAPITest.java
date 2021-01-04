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
package com.graphhopper;

import com.graphhopper.config.Profile;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperAPITest {
    void initGraph(GraphHopperStorage graph, FlagEncoder encoder) {
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 42, 10);
        na.setNode(1, 42.1, 10.1);
        na.setNode(2, 42.1, 10.2);
        na.setNode(3, 42, 10.4);

        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(10));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3).setDistance(10));
    }

    @Test
    public void testLoad() {
        final String profile = "profile";
        final String vehicle = "car";
        final String weighting = "fastest";
        EncodingManager em = EncodingManager.create(vehicle);
        FlagEncoder encoder = em.getEncoder(vehicle);
        GraphHopperStorage graph = new GraphBuilder(em).create();
        initGraph(graph, encoder);
        // do further changes:
        NodeAccess na = graph.getNodeAccess();
        na.setNode(4, 41.9, 10.2);

        GHUtility.setSpeed(60, true, false, encoder, graph.edge(1, 2).setDistance(10));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 4).setDistance(40));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(4, 3).setDistance(40));

        GraphHopper instance = createGraphHopper(vehicle).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(false).
                loadGraph(graph);
        // 3 -> 0
        GHResponse rsp = instance.route(new GHRequest(42, 10.4, 42, 10).setProfile(profile));
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        ResponsePath responsePath = rsp.getBest();
        assertEquals(80, responsePath.getDistance(), 1e-6);

        PointList points = responsePath.getPoints();
        assertEquals(42, points.getLat(0), 1e-5);
        assertEquals(10.4, points.getLon(0), 1e-5);
        assertEquals(41.9, points.getLat(1), 1e-5);
        assertEquals(10.2, points.getLon(1), 1e-5);
        assertEquals(3, points.getSize());
        instance.close();
    }

    @Test
    public void testDisconnected179() {
        final String profile = "profile";
        final String vehicle = "car";
        final String weighting = "fastest";
        EncodingManager em = EncodingManager.create(vehicle);
        GraphHopperStorage graph = new GraphBuilder(em).create();
        initGraph(graph, em.getEncoder(vehicle));

        GraphHopper instance = createGraphHopper(vehicle).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(false).
                loadGraph(graph);
        GHResponse rsp = instance.route(new GHRequest(42, 10, 42, 10.4).setProfile(profile));
        assertTrue(rsp.hasErrors());

        try {
            rsp.getBest().getPoints();
            fail();
        } catch (Exception ex) {
        }

        instance.close();
    }

    @Test
    public void testDoNotInterpolateTwice1645() {
        final String vehicle = "car";
        String loc = "./target/issue1645";
        Helper.removeDir(new File(loc));
        EncodingManager encodingManager = EncodingManager.create(vehicle);
        FlagEncoder encoder = encodingManager.getEncoder(vehicle);
        GraphHopperStorage graph = GraphBuilder.start(encodingManager).setRAM(loc, true).set3D(true).create();

        // we need elevation
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 42, 10, 10);
        na.setNode(1, 42.1, 10.1, 10);
        na.setNode(2, 42.1, 10.2, 1);
        na.setNode(3, 42, 10.4, 1);

        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(10));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(2, 3).setDistance(10));

        final AtomicInteger counter = new AtomicInteger(0);
        GraphHopper instance = createGraphHopper(vehicle)
                .setElevation(true)
                .setGraphHopperLocation(loc)
                .loadGraph(graph);
        instance.flush();
        instance.close();
        assertEquals(0, counter.get());

        instance = new GraphHopper() {
            @Override
            void interpolateBridgesTunnelsAndFerries() {
                counter.incrementAndGet();
                super.interpolateBridgesTunnelsAndFerries();
            }
        }
                .setEncodingManager(encodingManager)
                .setProfiles(new Profile(vehicle).setVehicle(vehicle).setWeighting("fastest"))
                .setElevation(true);
        instance.load(loc);
        instance.flush();
        instance.close();
        assertEquals(1, counter.get());

        instance = new GraphHopper() {
            @Override
            void interpolateBridgesTunnelsAndFerries() {
                counter.incrementAndGet();
                super.interpolateBridgesTunnelsAndFerries();
            }
        }
                .setEncodingManager(encodingManager)
                .setProfiles(new Profile(vehicle).setVehicle(vehicle).setWeighting("fastest"))
                .setElevation(true);
        instance.load(loc);
        instance.flush();
        instance.close();
        assertEquals(1, counter.get());

        Helper.removeDir(new File(loc));
    }

    @Test
    public void testNoLoad() {
        String profile = "profile";
        String vehicle = "car";
        String weighting = "fastest";
        GraphHopper instance = createGraphHopper(vehicle).
                setProfiles(new Profile(profile).setVehicle(vehicle).setVehicle(weighting)).
                setStoreOnFlush(false);
        try {
            instance.route(new GHRequest(42, 10.4, 42, 10).setProfile(profile));
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Do a successful call to load or importOrLoad before routing"));
        }

        instance = createGraphHopper(vehicle);
        try {
            instance.route(new GHRequest(42, 10.4, 42, 10).setProfile(profile));
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Do a successful call to load or importOrLoad before routing"));
        }
    }

    private GraphHopper createGraphHopper(String vehicle) {
        return new GraphHopper()
                .setEncodingManager(EncodingManager.create(vehicle))
                .setProfiles(new Profile(vehicle).setVehicle(vehicle).setWeighting("fastest"));
    }
}
