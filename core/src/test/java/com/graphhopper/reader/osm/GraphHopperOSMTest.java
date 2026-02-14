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
package com.graphhopper.reader.osm;

import com.graphhopper.*;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class GraphHopperOSMTest {
    private static final String ghLoc = "./target/tmp/ghosm";
    private static final String testOsm = "./src/test/resources/com/graphhopper/reader/osm/test-osm.xml";
    private static final String testOsm3 = "./src/test/resources/com/graphhopper/reader/osm/test-osm3.xml";
    private static final String testOsm8 = "./src/test/resources/com/graphhopper/reader/osm/test-osm8.xml";
    private GraphHopper instance;

    @BeforeEach
    public void setUp() {
        Helper.removeDir(new File(ghLoc));
    }

    @AfterEach
    public void tearDown() {
        if (instance != null)
            instance.close();
        Helper.removeDir(new File(ghLoc));
    }

    @Test
    public void testLoadOSM() {
        String profile = "car_profile";
        GraphHopper hopper = new GraphHopper().
                setStoreOnFlush(true).
                setProfiles(TestProfiles.constantSpeed(profile)).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(profile));
        hopper.importOrLoad();
        GHResponse rsp = hopper.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).
                setProfile(profile));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().size());

        hopper.close();

        // no encoding manager necessary
        hopper = new GraphHopper().
                setProfiles(TestProfiles.constantSpeed(profile)).
                setStoreOnFlush(true);
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(profile));
        hopper.setGraphHopperLocation(ghLoc);
        assertTrue(hopper.load());
        rsp = hopper.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).
                setProfile(profile));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().size());

        hopper.close();
        try {
            rsp = hopper.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4)
                    .setProfile(profile));
            fail();
        } catch (Exception ex) {
            assertEquals("You need to create a new GraphHopper instance as it is already closed", ex.getMessage());
        }

        try {
            hopper.getLocationIndex().findClosest(51.2492152, 9.4317166, EdgeFilter.ALL_EDGES);
            fail();
        } catch (Exception ex) {
            assertEquals("You need to create a new LocationIndex instance as it is already closed", ex.getMessage());
        }
    }

    @Test
    public void testLoadOSMNoCH() {
        final String profile = "profile";
        GraphHopper gh = new GraphHopper().
                setProfiles(TestProfiles.constantSpeed(profile)).
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        gh.importOrLoad();

        assertTrue(gh.getCHGraphs().isEmpty());

        GHResponse rsp = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4)
                .setProfile(profile));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().size());

        gh.close();
        gh = new GraphHopper().
                setProfiles(TestProfiles.constantSpeed(profile)).
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc);
        assertTrue(gh.load());
        rsp = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4)
                .setProfile(profile));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().size());

        gh.close();

        gh = new GraphHopper().
                setProfiles(TestProfiles.constantSpeed(profile)).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);

        assertTrue(gh.getCHGraphs().isEmpty());
        gh.close();
    }

    @Test
    public void testQueryLocationIndexWithBBox() {
        final GraphHopper gh = new GraphHopper().
                setProfiles(TestProfiles.constantSpeed("car")).
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc).
                setOSMFile("../core/files/monaco.osm.gz");
        gh.importOrLoad();

        final NodeAccess na = gh.getBaseGraph().getNodeAccess();
        final Collection<Integer> indexNodeList = new TreeSet<>();
        LocationIndexTree index = (LocationIndexTree) gh.getLocationIndex();
        final EdgeExplorer edgeExplorer = gh.getBaseGraph().createEdgeExplorer();
        final BBox bbox = new BBox(7.422, 7.429, 43.729, 43.734);
        index.query(bbox, edgeId -> {
            EdgeIteratorState edge = gh.getBaseGraph().getEdgeIteratorStateForKey(edgeId * 2);
            for (int i = 0; i < 2; i++) {
                int nodeId = i == 0 ? edge.getBaseNode() : edge.getAdjNode();
                double lat = na.getLat(nodeId);
                double lon = na.getLon(nodeId);
                if (bbox.contains(lat, lon))
                    indexNodeList.add(nodeId);
            }
        });

        assertEquals(152, indexNodeList.size());
        for (int nodeId : indexNodeList) {
            if (!bbox.contains(na.getLat(nodeId), na.getLon(nodeId)))
                fail("bbox " + bbox + " should contain " + nodeId);
        }

        final Collection<Integer> bfsNodeList = new TreeSet<>();
        new BreadthFirstSearch() {
            @Override
            protected GHBitSet createBitSet() {
                return new GHBitSetImpl(gh.getBaseGraph().getNodes());
            }

            @Override
            protected boolean goFurther(int nodeId) {
                double lat = na.getLat(nodeId);
                double lon = na.getLon(nodeId);
                if (bbox.contains(lat, lon))
                    bfsNodeList.add(nodeId);

                return true;
            }
        }.start(edgeExplorer, index.findClosest(43.731, 7.425, EdgeFilter.ALL_EDGES).getClosestNode());

        assertTrue(indexNodeList.size() >= bfsNodeList.size(), "index size: " + indexNodeList.size() + ", bfs size: " + bfsNodeList.size());
        assertTrue(indexNodeList.containsAll(bfsNodeList), "index size: " + indexNodeList.size() + ", bfs size: " + bfsNodeList.size());
    }

    @Test
    public void testLoadingWithDifferentCHConfig_issue471_pr1488() {
        // when there is a single CH profile we can also load GraphHopper without it
        // in #471 this was forbidden, but later it was allowed again, see #1488
        Profile profile = TestProfiles.constantSpeed("car");

        GraphHopper gh = new GraphHopper().
                setStoreOnFlush(true).
                setProfiles(profile).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        gh.getCHPreparationHandler().setCHProfiles(new CHProfile(profile.getName()));
        gh.importOrLoad();
        GHResponse rsp = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).setProfile("car"));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().size());
        gh.close();

        // now load GH without CH profile
        gh = new GraphHopper().
                setProfiles(profile).
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc);
        gh.load();
        // no error

        Helper.removeDir(new File(ghLoc));

        // when there is no CH preparation yet it will be added (CH delta import)
        gh = new GraphHopper().
                setStoreOnFlush(true).
                setProfiles(profile).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        gh.importOrLoad();
        rsp = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).setProfile("car"));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().size());
        gh.close();

        gh = new GraphHopper().
                setProfiles(new Profile(profile)).
                setStoreOnFlush(true);
        gh.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
        gh.setGraphHopperLocation(ghLoc);
        gh.importOrLoad();
        rsp = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).setProfile("car"));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().size());
        // no error
    }

    @Test
    public void testAllowMultipleReadingInstances() {
        GraphHopper instance1 = new GraphHopper().
                setProfiles(TestProfiles.constantSpeed("car")).
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        instance1.importOrLoad();

        GraphHopper instance2 = new GraphHopper().
                setProfiles(TestProfiles.constantSpeed("car")).
                setStoreOnFlush(true).
                setOSMFile(testOsm).
                setGraphHopperLocation(ghLoc);
        instance2.load();

        GraphHopper instance3 = new GraphHopper().
                setProfiles(TestProfiles.constantSpeed("car")).
                setStoreOnFlush(true).
                setOSMFile(testOsm).
                setGraphHopperLocation(ghLoc);
        instance3.load();

        instance1.close();
        instance2.close();
        instance3.close();
    }

    @Test
    public void testDoNotAllowWritingAndLoadingAtTheSameTime() throws Exception {
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        final GraphHopper instance1 = new GraphHopper() {
            @Override
            protected void importOSM() {
                try {
                    latch2.countDown();
                    latch1.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                super.importOSM();
            }
        }.setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc).
                setProfiles(TestProfiles.constantSpeed("car")).
                setOSMFile(testOsm);
        final AtomicReference<Exception> ar = new AtomicReference<>();
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    instance1.importOrLoad();
                } catch (Exception ex) {
                    ar.set(ex);
                }
            }
        };
        thread.start();

        GraphHopper instance2 = new GraphHopper().
                setProfiles(TestProfiles.constantSpeed("car")).
                setStoreOnFlush(true).
                setOSMFile(testOsm).
                setGraphHopperLocation(ghLoc);
        try {
            // let thread reach the CountDownLatch
            latch2.await(3, TimeUnit.SECONDS);
            // now importOrLoad should have create a lock which this load call does not like
            instance2.load();
            fail("There should have been an error because of the lock");
        } catch (RuntimeException ex) {
            assertNotNull(ex);
            assertTrue(ex.getMessage().startsWith("To avoid reading partial data"), ex.getMessage());
        } finally {
            instance2.close();
            latch1.countDown();
            // make sure the import process wasn't interrupted and no other error happened
            thread.join();
        }

        if (ar.get() != null)
            assertNull(ar.get(), ar.get().getMessage());
        instance1.close();
    }

    @Test
    public void testPrepare() {
        final String profile = "profile";

        instance = new GraphHopper().
                setStoreOnFlush(false).
                setProfiles(TestProfiles.constantSpeed("profile")).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        instance.getCHPreparationHandler().setCHProfiles(new CHProfile(profile));
        instance.importOrLoad();
        GHResponse rsp = instance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).
                setProfile(profile).
                setAlgorithm(DIJKSTRA_BI));
        assertFalse(rsp.hasErrors());
        assertEquals(Helper.createPointList(51.249215, 9.431716, 52.0, 9.0, 51.2, 9.4), rsp.getBest().getPoints());
        assertEquals(3, rsp.getBest().getPoints().size());
    }

    @Test
    public void testFootAndCar() {
        final String profile1 = "profile1";
        final String profile2 = "profile2";

        // now all ways are imported
        instance = new GraphHopper().
                setEncodedValuesString("car_access, car_average_speed, foot_access, foot_average_speed").
                setProfiles(
                        TestProfiles.accessAndSpeed(profile1, "car"),
                        TestProfiles.accessAndSpeed(profile2, "foot")
                ).
                setStoreOnFlush(false).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm8);
        instance.importOrLoad();

        // This test is arguably a bit unfair: It expects the LocationIndex
        // to find a foot edge that is many tiles away.
        // Previously, it worked, but only because of the way the LocationIndex would traverse the Graph
        // (it would also go into CAR edges to find WALK edges).
        // Now it doesn't work like that anymore, so I set this parameter so the test doesn't fail.
        ((LocationIndexTree) instance.getLocationIndex()).setMaxRegionSearch(300);

        assertEquals(5, instance.getBaseGraph().getNodes());
        assertEquals(8, instance.getBaseGraph().getEdges());

        // A to D
        GHResponse grsp = instance.route(new GHRequest(11.1, 50, 11.3, 51).setProfile(profile1));
        assertFalse(grsp.hasErrors(), grsp.getErrors().toString());
        ResponsePath rsp = grsp.getBest();
        assertEquals(2, rsp.getPoints().size());
        // => found A and D
        assertEquals(50, rsp.getPoints().getLon(0), 1e-3);
        assertEquals(11.1, rsp.getPoints().getLat(0), 1e-3);
        assertEquals(51, rsp.getPoints().getLon(1), 1e-3);
        assertEquals(11.3, rsp.getPoints().getLat(1), 1e-3);

        // A to D not allowed for foot. But the location index will choose a node close to D accessible to FOOT
        grsp = instance.route(new GHRequest(11.1, 50, 11.3, 51).setProfile(profile2));
        assertFalse(grsp.hasErrors());
        rsp = grsp.getBest();
        assertEquals(2, rsp.getPoints().size());
        // => found a point on edge A-B
        assertEquals(11.680, rsp.getPoints().getLat(1), 1e-3);
        assertEquals(50.644, rsp.getPoints().getLon(1), 1e-3);

        // A to E only for foot
        grsp = instance.route(new GHRequest(11.1, 50, 10, 51).setProfile(profile2));
        assertFalse(grsp.hasErrors());
        rsp = grsp.getBest();
        assertEquals(2, rsp.getPoints().size());

        // A D E for car
        grsp = instance.route(new GHRequest(11.1, 50, 10, 51).setProfile(profile1));
        assertFalse(grsp.hasErrors());
        rsp = grsp.getBest();
        assertEquals(3, rsp.getPoints().size());
    }

    @Test
    public void testUnloadProfile() {
        instance = new GraphHopper().init(
                        new GraphHopperConfig().
                                putObject("datareader.file", testOsm3).
                                putObject("datareader.dataaccess", "RAM").
                                putObject("import.osm.ignored_highways", "").
                                setProfiles(List.of(
                                        TestProfiles.constantSpeed("foot"),
                                        TestProfiles.constantSpeed("car")
                                ))).
                setGraphHopperLocation(ghLoc);
        instance.importOrLoad();
        assertEquals(5, instance.getBaseGraph().getNodes());
        instance.close();

        // we can run GH without the car profile
        GraphHopper tmpGH = new GraphHopper().init(
                        new GraphHopperConfig().
                                putObject("datareader.file", testOsm3).
                                putObject("datareader.dataaccess", "RAM").
                                putObject("import.osm.ignored_highways", "").
                                setProfiles(List.of(
                                        TestProfiles.constantSpeed("foot")
                                ))).
                setGraphHopperLocation(ghLoc);
        tmpGH.load();
        assertEquals(5, instance.getBaseGraph().getNodes());

        // different encoded values do not matter, since they are ignored when loading the graph anyway
        instance = new GraphHopper().init(
                        new GraphHopperConfig().
                                putObject("datareader.file", testOsm3).
                                putObject("datareader.dataaccess", "RAM").
                                putObject("graph.encoded_values", "road_class").
                                putObject("import.osm.ignored_highways", "").
                                setProfiles(List.of(
                                        TestProfiles.constantSpeed("car")
                                ))).
                setGraphHopperLocation(ghLoc);
        instance.load();
        assertEquals(5, instance.getBaseGraph().getNodes());
        assertEquals("road_class,road_environment,roundabout,car_access,road_class_link,max_speed,foot_subnetwork,car_subnetwork",
                instance.getEncodingManager().getEncodedValues().stream().map(EncodedValue::getName).collect(Collectors.joining(",")));
    }

    @Test
    public void testFailsForWrongEVConfig() {
        instance = new GraphHopper().init(
                        new GraphHopperConfig().
                                putObject("datareader.file", testOsm3).
                                putObject("datareader.dataaccess", "RAM").
                                putObject("import.osm.ignored_highways", "").
                                setProfiles(List.of(TestProfiles.constantSpeed("car")))).
                setGraphHopperLocation(ghLoc);
        instance.importOrLoad();
        // older versions <= 0.12 did not store this property, ensure that we fail to load it
        instance.getProperties().remove("graph.encoded_values");
        instance.getBaseGraph().flush();
        assertEquals(5, instance.getBaseGraph().getNodes());
        instance.close();

        // different encoded values are ignored anyway
        instance = new GraphHopper().init(
                        new GraphHopperConfig().
                                putObject("datareader.file", testOsm3).
                                putObject("datareader.dataaccess", "RAM").
                                putObject("graph.location", ghLoc).
                                putObject("graph.encoded_values", "road_environment,road_class").
                                putObject("import.osm.ignored_highways", "").
                                setProfiles(List.of(TestProfiles.constantSpeed("car")))).
                setOSMFile(testOsm3);
        instance.load();
        assertEquals(5, instance.getBaseGraph().getNodes());
        assertEquals("road_class,road_environment,roundabout,car_access,road_class_link,max_speed,car_subnetwork",
                instance.getEncodingManager().getEncodedValues().stream().map(EncodedValue::getName).collect(Collectors.joining(",")));
    }

    @Test
    public void testNoNPE_ifLoadNotSuccessful() {
        String profile = "profile";
        instance = new GraphHopper().
                setProfiles(new Profile(profile)).
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc);
        try {
            // loading from empty directory
            new File(ghLoc).mkdirs();
            assertFalse(instance.load());
            instance.route(new GHRequest(10, 40, 12, 32).setProfile(profile));
            fail();
        } catch (IllegalStateException ex) {
            assertEquals("Do a successful call to load or importOrLoad before routing", ex.getMessage());
        }
    }

    @Test
    public void testDoesNotCreateEmptyFolderIfLoadingFromNonExistingPath() {
        instance = new GraphHopper();
        instance.setProfiles(new Profile("car"));
        instance.setGraphHopperLocation(ghLoc);
        assertFalse(instance.load());
        assertFalse(new File(ghLoc).exists());
    }

    @Test
    public void testFailsForMissingParameters() {
        // missing load of graph
        instance = new GraphHopper();
        instance.setOSMFile(testOsm);
        Exception ex = assertThrows(IllegalStateException.class, instance::importOrLoad);
        assertEquals("GraphHopperLocation is not specified. Call setGraphHopperLocation or init before", ex.getMessage());

        // missing graph location
        instance = new GraphHopper();
        ex = assertThrows(IllegalStateException.class, instance::importOrLoad);
        assertEquals("GraphHopperLocation is not specified. Call setGraphHopperLocation or init before", ex.getMessage());

        // missing OSM file to import
        instance = new GraphHopper().
                setProfiles(TestProfiles.constantSpeed("car")).
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc);
        ex = assertThrows(IllegalStateException.class, instance::importOrLoad);
        assertEquals("Couldn't load from existing folder: " + ghLoc
                + " but also cannot use file for DataReader as it wasn't specified!", ex.getMessage());

        // missing profiles
        instance = new GraphHopper().
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm3);
        ex = assertThrows(IllegalArgumentException.class, instance::importOrLoad);
        assertTrue(ex.getMessage().startsWith("There has to be at least one profile"), ex.getMessage());

        // Import is possible even if no storeOnFlush is specified BUT here we miss the OSM file
        instance = new GraphHopper().
                setProfiles(TestProfiles.constantSpeed("car")).
                setStoreOnFlush(false).
                setGraphHopperLocation(ghLoc);
        ex = assertThrows(IllegalStateException.class, instance::importOrLoad);
        assertEquals("Couldn't load from existing folder: " + ghLoc
                + " but also cannot use file for DataReader as it wasn't specified!", ex.getMessage());
    }

    @Test
    public void testFootOnly() {
        // now only footable ways are imported => no A D C and B D E => the other both ways have pillar nodes!
        final String profile = "foot_profile";
        instance = new GraphHopper().
                setStoreOnFlush(false).
                setEncodedValuesString("foot_access, foot_priority, foot_average_speed").
                setProfiles(TestProfiles.accessSpeedAndPriority(profile, "foot")).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm3);
        // exclude motorways which aren't accessible for foot
        instance.getReaderConfig().setIgnoredHighways(List.of("motorway"));
        instance.getCHPreparationHandler().setCHProfiles(new CHProfile(profile));
        instance.importOrLoad();

        ((LocationIndexTree) instance.getLocationIndex()).setMaxRegionSearch(300);

        assertEquals(2, instance.getBaseGraph().getNodes());
        assertEquals(2, instance.getBaseGraph().getAllEdges().length());

        // A to E only for foot
        GHResponse grsp = instance.route(new GHRequest(11.1, 50, 11.19, 52).setProfile(profile));
        assertFalse(grsp.hasErrors());
        ResponsePath rsp = grsp.getBest();
        // the last points snaps to the edge
        assertEquals(Helper.createPointList(11.1, 50, 10, 51, 11.194015, 51.995013), rsp.getPoints());
    }

    @Test
    public void testVia() {
        final String profile = "profile";

        instance = new GraphHopper().setStoreOnFlush(true).
                init(new GraphHopperConfig().
                        putObject("datareader.file", testOsm3).
                        putObject("prepare.min_network_size", 0).
                        putObject("graph.encoded_values", "car_access, car_average_speed").
                        putObject("import.osm.ignored_highways", "").
                        setProfiles(List.of(TestProfiles.accessAndSpeed(profile, "car"))).
                        setCHProfiles(Collections.singletonList(new CHProfile(profile)))
                ).
                setGraphHopperLocation(ghLoc);
        instance.importOrLoad();

        // A -> B -> C
        GHPoint first = new GHPoint(11.1, 50);
        GHPoint second = new GHPoint(12, 51);
        GHPoint third = new GHPoint(11.2, 51.9);
        GHResponse rsp12 = instance.route(new GHRequest(first, second).setProfile(profile));
        assertFalse(rsp12.hasErrors(), "should find 1->2");
        assertEquals(147930.5, rsp12.getBest().getDistance(), .1);
        GHResponse rsp23 = instance.route(new GHRequest(second, third).setProfile(profile));
        assertFalse(rsp23.hasErrors(), "should find 2->3");
        assertEquals(176608.9, rsp23.getBest().getDistance(), .1);

        GHResponse grsp = instance.route(new GHRequest(Arrays.asList(first, second, third)).setProfile(profile));
        assertFalse(grsp.hasErrors(), "should find 1->2->3");
        ResponsePath rsp = grsp.getBest();
        assertEquals(rsp12.getBest().getDistance() + rsp23.getBest().getDistance(), rsp.getDistance(), 1e-6);
        assertEquals(4, rsp.getPoints().size());
        assertEquals(5, rsp.getInstructions().size());
        assertEquals(Instruction.REACHED_VIA, rsp.getInstructions().get(1).getSign());
    }

    @Test
    public void testMultipleCHPreparationsInParallel() {
        HashMap<String, Integer> shortcutCountMap = new HashMap<>();
        // try all parallelization modes
        for (int threadCount = 1; threadCount < 6; threadCount++) {
            GraphHopper hopper = new GraphHopper().
                    setStoreOnFlush(false).
                    setProfiles(
                            TestProfiles.constantSpeed("p1", 60),
                            TestProfiles.constantSpeed("p2", 70),
                            TestProfiles.constantSpeed("p3", 80),
                            TestProfiles.constantSpeed("p4", 90)
                    ).
                    setGraphHopperLocation(ghLoc).
                    setOSMFile(testOsm);
            hopper.getCHPreparationHandler()
                    .setCHProfiles(
                            new CHProfile("p1"),
                            new CHProfile("p2"),
                            new CHProfile("p3"),
                            new CHProfile("p4")
                    )
                    .setPreparationThreads(threadCount);

            hopper.importOrLoad();

            assertEquals(4, hopper.getCHGraphs().size());
            for (Map.Entry<String, RoutingCHGraph> chGraph : hopper.getCHGraphs().entrySet()) {
                String name = chGraph.getKey();
                Integer shortcutCount = shortcutCountMap.get(name);
                if (shortcutCount == null)
                    shortcutCountMap.put(name, chGraph.getValue().getShortcuts());
                else
                    assertEquals((long) shortcutCount, chGraph.getValue().getShortcuts());

                String keyError = Parameters.CH.PREPARE + "error." + name;
                String valueError = hopper.getProperties().get(keyError);
                assertTrue(valueError.isEmpty(), "Properties for " + name + " should NOT contain error " + valueError + " [" + threadCount + "]");

                String key = Parameters.CH.PREPARE + "date." + name;
                String value = hopper.getProperties().get(key);
                assertFalse(value.isEmpty(), "Properties for " + name + " did NOT contain finish date [" + threadCount + "]");
            }
            hopper.close();
        }
    }

    @Test
    public void testMultipleLMPreparationsInParallel() {
        HashMap<String, Integer> landmarkCount = new HashMap<>();
        // try all parallelization modes
        for (int threadCount = 1; threadCount < 6; threadCount++) {
            GraphHopper hopper = new GraphHopper().
                    setStoreOnFlush(false).
                    setProfiles(Arrays.asList(
                            TestProfiles.constantSpeed("p1", 60),
                            TestProfiles.constantSpeed("p2", 70),
                            TestProfiles.constantSpeed("p3", 80),
                            TestProfiles.constantSpeed("p4", 90)
                    )).
                    setGraphHopperLocation(ghLoc).
                    setOSMFile(testOsm);
            hopper.getLMPreparationHandler().
                    setLMProfiles(
                            new LMProfile("p1"),
                            new LMProfile("p2"),
                            new LMProfile("p3"),
                            new LMProfile("p4")
                    ).
                    setPreparationThreads(threadCount);

            hopper.importOrLoad();

            assertEquals(4, hopper.getLandmarks().size());
            for (Map.Entry<String, LandmarkStorage> landmarks : hopper.getLandmarks().entrySet()) {
                String name = landmarks.getKey();
                Integer landmarksCount = landmarkCount.get(name);
                if (landmarksCount == null)
                    landmarkCount.put(name, landmarks.getValue().getSubnetworksWithLandmarks());
                else
                    assertEquals((int) landmarksCount, landmarks.getValue().getSubnetworksWithLandmarks());

                String keyError = Parameters.Landmark.PREPARE + "error." + name;
                String valueError = hopper.getProperties().get(keyError);
                assertTrue(valueError.isEmpty(), "Properties for " + name + " should NOT contain error " + valueError + " [" + threadCount + "]");

                String key = Parameters.Landmark.PREPARE + "date." + name;
                String value = hopper.getProperties().get(key);
                assertFalse(value.isEmpty(), "Properties for " + name + " did NOT contain finish date [" + threadCount + "]");
            }
            hopper.close();
        }
    }

    @Test
    public void testMultipleProfilesForCH() {
        GraphHopper hopper = new GraphHopper().
                setProfiles(
                        TestProfiles.constantSpeed("profile1", 60),
                        TestProfiles.constantSpeed("profile2", 100)
                ).
                setStoreOnFlush(false).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        hopper.getCHPreparationHandler().setCHProfiles(
                new CHProfile("profile1"), new CHProfile("profile2")
        );
        hopper.importOrLoad();
        assertEquals(2, hopper.getCHGraphs().size());
    }

    @Test
    public void testProfilesMustNotBeChanged() {
        {
            GraphHopper hopper = createHopperWithProfiles(List.of(
                    TestProfiles.constantSpeed("bike1", 60),
                    TestProfiles.constantSpeed("bike2", 120)
            ));
            hopper.importOrLoad();
            hopper.close();
        }
        {
            // load without problem
            GraphHopper hopper = createHopperWithProfiles(List.of(
                    TestProfiles.constantSpeed("bike1", 60),
                    TestProfiles.constantSpeed("bike2", 120)
            ));
            hopper.importOrLoad();
            hopper.close();
        }
        {
            // problem: the profile changed (slightly). we do not allow this because we would potentially need to re-calculate the subnetworks
            GraphHopper hopper = createHopperWithProfiles(List.of(
                    TestProfiles.constantSpeed("bike1", 60),
                    TestProfiles.constantSpeed("bike2", 110)
            ));
            IllegalStateException e = assertThrows(IllegalStateException.class, hopper::importOrLoad);
            assertTrue(e.getMessage().contains("Profile 'bike2' does not match"), e.getMessage());
            hopper.close();
        }
        {
            // problem: we add another profile, which is not allowed either, because there would be no subnetwork ev for it
            GraphHopper hopper = createHopperWithProfiles(List.of(
                    TestProfiles.constantSpeed("bike1", 60),
                    TestProfiles.constantSpeed("bike2", 120),
                    TestProfiles.constantSpeed("bike3", 110)
            ));
            IllegalStateException e = assertThrows(IllegalStateException.class, hopper::importOrLoad);
            assertTrue(e.getMessage().contains("You cannot add new profiles to the loaded graph. Profile 'bike3' is new"), e.getMessage());
            hopper.close();
        }
        {
            // disabling a profile by not 'loading' it is ok
            GraphHopper hopper = createHopperWithProfiles(List.of(
                    TestProfiles.constantSpeed("bike2", 120)
            ));
            hopper.importOrLoad();
            assertEquals(1, hopper.getProfiles().size());
            assertEquals("bike2", hopper.getProfiles().get(0).getName());
            hopper.close();
        }
    }

    private GraphHopper createHopperWithProfiles(List<Profile> profiles) {
        GraphHopper hopper = new GraphHopper();
        hopper.init(new GraphHopperConfig()
                .putObject("graph.location", ghLoc)
                .putObject("datareader.file", testOsm)
                .putObject("import.osm.ignored_highways", "")
                .setProfiles(profiles)
        );
        return hopper;
    }

    @Test
    public void testLoadingLMAndCHProfiles() {
        Profile profile = TestProfiles.constantSpeed("car");
        GraphHopper hopper = new GraphHopper()
                .setGraphHopperLocation(ghLoc)
                .setOSMFile(testOsm)
                .setProfiles(profile);
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("car"));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
        hopper.importOrLoad();
        hopper.close();

        // load without problem
        hopper = new GraphHopper().setProfiles(profile);
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("car"));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
        hopper.setGraphHopperLocation(ghLoc);
        assertTrue(hopper.load());
        hopper.close();

        // we have to manipulate the props file to set up a situation where the LM/CH preparations do not match the
        // profiles, because changing the profiles would be an error in itself
        StorableProperties props = new StorableProperties(new GHDirectory(ghLoc, DAType.RAM_STORE));
        props.loadExisting();
        props.put("graph.profiles.ch.car.version", 404);
        props.put("graph.profiles.lm.car.version", 505);
        props.flush();

        // problem: LM version does not match the actual profile
        hopper = new GraphHopper().setProfiles(profile);
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("car"));
        hopper.setGraphHopperLocation(ghLoc);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, hopper::load);
        assertEquals("LM preparation of car already exists in storage and doesn't match configuration", ex.getMessage());
        hopper.close();

        // problem: CH version does not match the actual profile
        hopper = new GraphHopper().setProfiles(profile);
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
        hopper.setGraphHopperLocation(ghLoc);
        ex = assertThrows(IllegalArgumentException.class, hopper::load);
        assertEquals("CH preparation of car already exists in storage and doesn't match configuration", ex.getMessage());
        hopper.close();
    }
}
