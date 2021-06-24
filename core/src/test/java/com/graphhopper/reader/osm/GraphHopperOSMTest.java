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
import com.graphhopper.routing.ch.CHPreparationHandler;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.storage.CHConfig;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
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
        String vehicle = "car";
        String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setStoreOnFlush(true).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
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
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true);
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(profile));
        assertTrue(hopper.load(ghLoc));
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
        final String vehicle = "car";
        final String weighting = "fastest";
        GraphHopper gh = new GraphHopper().
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        gh.importOrLoad();

        assertTrue(gh.getCHPreparationHandler().getPreparations().isEmpty());

        GHResponse rsp = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4)
                .setProfile(profile));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().size());

        gh.close();
        gh = new GraphHopper().
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true);
        assertTrue(gh.load(ghLoc));
        rsp = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4)
                .setProfile(profile));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().size());

        gh.close();

        gh = new GraphHopper().
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);

        assertTrue(gh.getCHPreparationHandler().getPreparations().isEmpty());
        gh.close();
    }

    @Test
    public void testQueryLocationIndexWithBBox() {
        final GraphHopper gh = new GraphHopper().
                setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest")).
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc).
                setOSMFile("../core/files/monaco.osm.gz");
        gh.importOrLoad();

        final NodeAccess na = gh.getGraphHopperStorage().getNodeAccess();
        final Collection<Integer> indexNodeList = new TreeSet<>();
        LocationIndexTree index = (LocationIndexTree) gh.getLocationIndex();
        final EdgeExplorer edgeExplorer = gh.getGraphHopperStorage().createEdgeExplorer();
        final BBox bbox = new BBox(7.422, 7.429, 43.729, 43.734);
        index.query(bbox, edgeId -> {
            EdgeIteratorState edge = gh.getGraphHopperStorage().getEdgeIteratorStateForKey(edgeId * 2);
            for (int i = 0; i < 2; i++) {
                int nodeId = i == 0 ? edge.getBaseNode() : edge.getAdjNode();
                double lat = na.getLat(nodeId);
                double lon = na.getLon(nodeId);
                if (bbox.contains(lat, lon))
                    indexNodeList.add(nodeId);
            }
        });

        assertEquals(57, indexNodeList.size());
        for (int nodeId : indexNodeList) {
            if (!bbox.contains(na.getLat(nodeId), na.getLon(nodeId)))
                fail("bbox " + bbox + " should contain " + nodeId);
        }

        final Collection<Integer> bfsNodeList = new TreeSet<>();
        new BreadthFirstSearch() {
            @Override
            protected GHBitSet createBitSet() {
                return new GHBitSetImpl(gh.getGraphHopperStorage().getNodes());
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
        final String profile = "profile";
        final String vehicle = "car";
        final String weighting = "fastest";
        GraphHopper gh = new GraphHopper().
                setStoreOnFlush(true).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        gh.getCHPreparationHandler().setCHProfiles(new CHProfile(profile));
        gh.importOrLoad();
        GHResponse rsp = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).setProfile(profile));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().size());
        gh.close();

        // now load GH without CH profile
        gh = new GraphHopper().
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true);
        gh.load(ghLoc);
        // no error

        Helper.removeDir(new File(ghLoc));

        // when there is no CH preparation we get an error if we try to load GH with a CH profile
        gh = new GraphHopper().
                setStoreOnFlush(true).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        gh.importOrLoad();
        rsp = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).setProfile(profile));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().size());
        gh.close();

        gh = new GraphHopper().
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true);
        gh.getCHPreparationHandler().setCHProfiles(new CHProfile("profile"));

        try {
            gh.load(ghLoc);
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("is not contained in loaded CH profiles"), ex.getMessage());
        }
    }

    @Test
    public void testAllowMultipleReadingInstances() {
        String vehicle = "car";
        GraphHopper instance1 = new GraphHopper().
                setProfiles(new Profile(vehicle).setVehicle(vehicle).setWeighting("fastest")).
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        instance1.importOrLoad();

        GraphHopper instance2 = new GraphHopper().
                setStoreOnFlush(true).
                setOSMFile(testOsm);
        instance2.load(ghLoc);

        GraphHopper instance3 = new GraphHopper().
                setStoreOnFlush(true).
                setOSMFile(testOsm);
        instance3.load(ghLoc);

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
                setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest")).
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
                setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest")).
                setStoreOnFlush(true).
                setOSMFile(testOsm);
        try {
            // let thread reach the CountDownLatch
            latch2.await(3, TimeUnit.SECONDS);
            // now importOrLoad should have create a lock which this load call does not like
            instance2.load(ghLoc);
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
        final String vehicle = "car";
        final String weighting = "shortest";

        instance = new GraphHopper().
                setStoreOnFlush(false).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
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
    public void testSortedGraph_noCH() {
        final String profile = "profile";
        final String vehicle = "car";
        final String weighting = "fastest";
        instance = new GraphHopper().
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(false).
                setSortGraph(true).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        instance.importOrLoad();
        ResponsePath rsp = instance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).
                setProfile(profile).
                setAlgorithm(DIJKSTRA_BI)).getBest();
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getPoints().size());
        assertEquals(new GHPoint(51.24921503475044, 9.431716451757769), rsp.getPoints().get(0));
        assertEquals(new GHPoint(52.0, 9.0), rsp.getPoints().get(1));
        assertEquals(new GHPoint(51.199999850988384, 9.39999970197677), rsp.getPoints().get(2));

        GHRequest req = new GHRequest(51.2492152, 9.4317166, 51.2, 9.4);
        req.setProfile(profile);
        boolean old = instance.getEncodingManager().isEnableInstructions();
        req.putHint("instructions", true);
        instance.route(req);
        assertEquals(old, instance.getEncodingManager().isEnableInstructions());

        req.putHint("instructions", false);
        instance.route(req);
        assertEquals(old, instance.getEncodingManager().isEnableInstructions(), "route method should not change instance field");
    }

    @Test
    public void testFootAndCar() {
        final String profile1 = "profile1";
        final String profile2 = "profile2";
        final String vehicle1 = "car";
        final String vehicle2 = "foot";
        final String weighting = "fastest";

        // now all ways are imported
        instance = new GraphHopper().
                setProfiles(
                        new Profile(profile1).setVehicle(vehicle1).setWeighting(weighting),
                        new Profile(profile2).setVehicle(vehicle2).setWeighting(weighting)
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

        assertEquals(5, instance.getGraphHopperStorage().getNodes());
        assertEquals(8, instance.getGraphHopperStorage().getEdges());

        // A to D
        GHResponse grsp = instance.route(new GHRequest(11.1, 50, 11.3, 51).setProfile(profile1));
        assertFalse(grsp.hasErrors(), grsp.getErrors().toString());
        ResponsePath rsp = grsp.getBest();
        assertEquals(3, rsp.getPoints().size());
        // => found A and D
        assertEquals(50, rsp.getPoints().getLon(0), 1e-3);
        assertEquals(11.1, rsp.getPoints().getLat(0), 1e-3);
        assertEquals(51, rsp.getPoints().getLon(2), 1e-3);
        assertEquals(11.3, rsp.getPoints().getLat(2), 1e-3);

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
    public void testFailsForWrongConfig() {
        instance = new GraphHopper().init(
                new GraphHopperConfig().
                        putObject("datareader.file", testOsm3).
                        putObject("datareader.dataaccess", "RAM").
                        putObject("graph.flag_encoders", "foot,car").
                        setProfiles(Arrays.asList(
                                new Profile("foot").setVehicle("foot").setWeighting("fastest"),
                                new Profile("car").setVehicle("car").setWeighting("fastest")
                        ))).
                setGraphHopperLocation(ghLoc);
        instance.importOrLoad();
        assertEquals(5, instance.getGraphHopperStorage().getNodes());
        instance.close();

        // different config (flagEncoder list)
        try {
            GraphHopper tmpGH = new GraphHopper().init(
                    new GraphHopperConfig().
                            putObject("datareader.file", testOsm3).
                            putObject("datareader.dataaccess", "RAM").
                            putObject("graph.flag_encoders", "foot").
                            setProfiles(Collections.singletonList(
                                    new Profile("foot").setVehicle("foot").setWeighting("fastest")
                            ))).
                    setOSMFile(testOsm3);
            tmpGH.load(ghLoc);
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage().startsWith("Encoding does not match"), ex.getMessage());
        }

        // different order is no longer okay, see #350
        try {
            GraphHopper tmpGH = new GraphHopper().init(new GraphHopperConfig().
                    putObject("datareader.file", testOsm3).
                    putObject("datareader.dataaccess", "RAM").
                    putObject("graph.flag_encoders", "car,foot").
                    setProfiles(Arrays.asList(
                            new Profile("car").setVehicle("car").setWeighting("fastest"),
                            new Profile("foot").setVehicle("foot").setWeighting("fastest")
                    ))).
                    setOSMFile(testOsm3);
            tmpGH.load(ghLoc);
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage().startsWith("Encoding does not match"), ex.getMessage());
        }

        // different encoded values should fail to load
        instance = new GraphHopper().init(
                new GraphHopperConfig().
                        putObject("datareader.file", testOsm3).
                        putObject("datareader.dataaccess", "RAM").
                        putObject("graph.encoded_values", "road_class").
                        putObject("graph.flag_encoders", "foot,car").
                        setProfiles(Arrays.asList(
                                new Profile("foot").setVehicle("foot").setWeighting("fastest"),
                                new Profile("car").setVehicle("car").setWeighting("fastest")
                        ))).
                setOSMFile(testOsm3);
        try {
            instance.load(ghLoc);
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage().startsWith("Encoded values do not match"), ex.getMessage());
        }

        // different version for car should fail
        instance = new GraphHopper();
        instance.getEncodingManagerBuilder().add(new FootFlagEncoder()).
                add(new CarFlagEncoder() {
                    @Override
                    public int getVersion() {
                        return 0;
                    }
                });
        instance.init(new GraphHopperConfig().
                putObject("datareader.file", testOsm3).
                putObject("datareader.dataaccess", "RAM").
                setProfiles(Collections.singletonList(new Profile("car").setVehicle("car").setWeighting("fastest")))).
                setOSMFile(testOsm3);
        try {
            instance.load(ghLoc);
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage().startsWith("Encoding does not match"), ex.getMessage());
        }
    }

    @Test
    public void testFailsForWrongEVConfig() {
        instance = new GraphHopper().init(
                new GraphHopperConfig().
                        putObject("datareader.file", testOsm3).
                        putObject("datareader.dataaccess", "RAM").
                        putObject("graph.flag_encoders", "foot,car").
                        setProfiles(Arrays.asList(
                                new Profile("foot").setVehicle("foot").setWeighting("fastest"),
                                new Profile("car").setVehicle("car").setWeighting("fastest")
                        ))).
                setGraphHopperLocation(ghLoc);
        instance.importOrLoad();
        // older versions <= 0.12 did not store this property, ensure that we fail to load it
        instance.getGraphHopperStorage().getProperties().remove("graph.encoded_values");
        instance.getGraphHopperStorage().flush();
        assertEquals(5, instance.getGraphHopperStorage().getNodes());
        instance.close();

        // different encoded values should fail to load
        instance = new GraphHopper().init(
                new GraphHopperConfig().
                        putObject("datareader.file", testOsm3).
                        putObject("datareader.dataaccess", "RAM").
                        putObject("graph.encoded_values", "road_environment,road_class").
                        putObject("graph.flag_encoders", "foot,car").
                        setProfiles(Arrays.asList(
                                new Profile("foot").setVehicle("foot").setWeighting("fastest"),
                                new Profile("car").setVehicle("car").setWeighting("fastest")
                        ))).
                setOSMFile(testOsm3);
        try {
            instance.load(ghLoc);
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage().startsWith("Encoded values do not match"), ex.getMessage());
        }
    }

    @Test
    public void testNoNPE_ifLoadNotSuccessful() {
        String profile = "profile";
        String vehicle = "car";
        String weighting = "fastest";
        instance = new GraphHopper().
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true);
        try {
            // loading from empty directory
            new File(ghLoc).mkdirs();
            assertFalse(instance.load(ghLoc));
            instance.route(new GHRequest(10, 40, 12, 32).setProfile(profile));
            fail();
        } catch (IllegalStateException ex) {
            assertEquals("Do a successful call to load or importOrLoad before routing", ex.getMessage());
        }
    }

    @Test
    public void testDoesNotCreateEmptyFolderIfLoadingFromNonExistingPath() {
        instance = new GraphHopper();
        instance.setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest"));
        assertFalse(instance.load(ghLoc));
        assertFalse(new File(ghLoc).exists());
    }

    @Test
    public void testFailsForMissingParameters() {
        // missing load of graph
        instance = new GraphHopper();
        try {
            instance.setOSMFile(testOsm);
            instance.importOrLoad();
            fail();
        } catch (IllegalStateException ex) {
            assertEquals("GraphHopperLocation is not specified. Call setGraphHopperLocation or init before", ex.getMessage());
        }

        // missing graph location
        instance = new GraphHopper();
        try {
            instance.importOrLoad();
            fail();
        } catch (IllegalStateException ex) {
            assertEquals("GraphHopperLocation is not specified. Call setGraphHopperLocation or init before", ex.getMessage());
        }

        // missing OSM file to import
        instance = new GraphHopper().
                setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest")).
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc);
        try {
            instance.importOrLoad();
            fail();
        } catch (IllegalStateException ex) {
            assertEquals("Couldn't load from existing folder: " + ghLoc
                    + " but also cannot use file for DataReader as it wasn't specified!", ex.getMessage());
        }

        // missing encoding manager          
        instance = new GraphHopper().
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm3);
        try {
            instance.importOrLoad();
            fail();
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().startsWith("no profiles exist but assumed to create EncodingManager"), ex.getMessage());
        }

        // Import is possible even if no storeOnFlush is specified BUT here we miss the OSM file
        instance = new GraphHopper().
                setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest")).
                setStoreOnFlush(false).
                setGraphHopperLocation(ghLoc);
        try {
            instance.importOrLoad();
            fail();
        } catch (Exception ex) {
            assertEquals("Couldn't load from existing folder: " + ghLoc
                    + " but also cannot use file for DataReader as it wasn't specified!", ex.getMessage());
        }
    }

    @Test
    public void testFootOnly() {
        // now only footable ways are imported => no A D C and B D E => the other both ways have pillar nodes!
        final String profile = "foot_profile";
        final String vehicle = "foot";
        final String weighting = "fastest";
        instance = new GraphHopper().
                setStoreOnFlush(false).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm3);
        instance.getCHPreparationHandler().setCHProfiles(new CHProfile(profile));
        instance.importOrLoad();

        ((LocationIndexTree) instance.getLocationIndex()).setMaxRegionSearch(300);

        assertEquals(2, instance.getGraphHopperStorage().getNodes());
        assertEquals(2, instance.getGraphHopperStorage().getAllEdges().length());

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
        final String vehicle = "car";
        final String weighting = "fastest";
        instance = new GraphHopper().setStoreOnFlush(true).
                init(new GraphHopperConfig().
                        putObject("datareader.file", testOsm3).
                        putObject("prepare.min_network_size", 0).
                        putObject("graph.flag_encoders", vehicle).
                        setProfiles(Collections.singletonList(new Profile(profile).setVehicle(vehicle).setWeighting(weighting))).
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
        HashMap<String, Long> shortcutCountMap = new HashMap<>();
        // try all parallelization modes        
        for (int threadCount = 1; threadCount < 6; threadCount++) {
            GraphHopper hopper = new GraphHopper().
                    setStoreOnFlush(false).
                    setProfiles(
                            new Profile("car_profile").setVehicle("car").setWeighting("fastest"),
                            new Profile("moto_profile").setVehicle("motorcycle").setWeighting("fastest"),
                            new Profile("mtb_profile").setVehicle("mtb").setWeighting("fastest"),
                            new Profile("bike_profile").setVehicle("racingbike").setWeighting("fastest"),
                            new Profile("foot_profile").setVehicle("foot").setWeighting("fastest")
                    ).
                    setGraphHopperLocation(ghLoc).
                    setOSMFile(testOsm);
            hopper.getCHPreparationHandler()
                    .setCHProfiles(
                            new CHProfile("car_profile"),
                            new CHProfile("moto_profile"),
                            new CHProfile("mtb_profile"),
                            new CHProfile("bike_profile"),
                            new CHProfile("foot_profile")
                    )
                    .setPreparationThreads(threadCount);

            hopper.importOrLoad();

            assertEquals(5, hopper.getCHPreparationHandler().getPreparations().size());
            for (PrepareContractionHierarchies pch : hopper.getCHPreparationHandler().getPreparations()) {
                assertTrue(pch.isPrepared(), "Preparation wasn't run! [" + threadCount + "]");

                String name = pch.getCHConfig().toFileName();
                Long singleThreadShortcutCount = shortcutCountMap.get(name);
                if (singleThreadShortcutCount == null)
                    shortcutCountMap.put(name, pch.getShortcuts());
                else
                    assertEquals((long) singleThreadShortcutCount, pch.getShortcuts());

                String keyError = Parameters.CH.PREPARE + "error." + name;
                String valueError = hopper.getGraphHopperStorage().getProperties().get(keyError);
                assertTrue(valueError.isEmpty(), "Properties for " + name + " should NOT contain error " + valueError + " [" + threadCount + "]");

                String key = Parameters.CH.PREPARE + "date." + name;
                String value = hopper.getGraphHopperStorage().getProperties().get(key);
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
                            new Profile("car_profile").setVehicle("car").setWeighting("fastest"),
                            new Profile("moto_profile").setVehicle("motorcycle").setWeighting("fastest"),
                            new Profile("mtb_profile").setVehicle("mtb").setWeighting("fastest"),
                            new Profile("bike_profile").setVehicle("racingbike").setWeighting("fastest"),
                            new Profile("foot_profile").setVehicle("foot").setWeighting("fastest")
                    )).
                    setGraphHopperLocation(ghLoc).
                    setOSMFile(testOsm);
            hopper.getLMPreparationHandler().
                    setLMProfiles(
                            new LMProfile("car_profile"),
                            new LMProfile("moto_profile"),
                            new LMProfile("mtb_profile"),
                            new LMProfile("bike_profile"),
                            new LMProfile("foot_profile")
                    ).
                    setPreparationThreads(threadCount);

            hopper.importOrLoad();

            assertEquals(5, hopper.getLMPreparationHandler().getPreparations().size());
            for (PrepareLandmarks prepLM : hopper.getLMPreparationHandler().getPreparations()) {
                assertTrue(prepLM.isPrepared(), "Preparation wasn't run! [" + threadCount + "]");

                String name = prepLM.getLMConfig().getName();
                Integer singleThreadShortcutCount = landmarkCount.get(name);
                if (singleThreadShortcutCount == null)
                    landmarkCount.put(name, prepLM.getSubnetworksWithLandmarks());
                else
                    assertEquals((int) singleThreadShortcutCount, prepLM.getSubnetworksWithLandmarks());

                String keyError = Parameters.Landmark.PREPARE + "error." + name;
                String valueError = hopper.getGraphHopperStorage().getProperties().get(keyError);
                assertTrue(valueError.isEmpty(), "Properties for " + name + " should NOT contain error " + valueError + " [" + threadCount + "]");

                String key = Parameters.Landmark.PREPARE + "date." + name;
                String value = hopper.getGraphHopperStorage().getProperties().get(key);
                assertFalse(value.isEmpty(), "Properties for " + name + " did NOT contain finish date [" + threadCount + "]");
            }
            hopper.close();
        }
    }

    @Test
    public void testGetWeightingForCH() {
        FlagEncoder truck = new CarFlagEncoder() {
            @Override
            public String toString() {
                return "truck";
            }
        };
        FlagEncoder simpleTruck = new CarFlagEncoder() {
            @Override
            public String toString() {
                return "simple_truck";
            }
        };

        // use simple truck first
        EncodingManager em = EncodingManager.create(simpleTruck, truck);
        CHPreparationHandler chHandler = new CHPreparationHandler();
        Weighting fwSimpleTruck = new FastestWeighting(simpleTruck);
        Weighting fwTruck = new FastestWeighting(truck);
        CHConfig simpleTruckConfig = CHConfig.nodeBased("simple_truck", fwSimpleTruck);
        CHConfig truckConfig = CHConfig.nodeBased("truck", fwTruck);
        GraphHopperStorage storage = new GraphBuilder(em).setCHConfigs(Arrays.asList(simpleTruckConfig, truckConfig)).build();
        chHandler.addCHConfig(simpleTruckConfig);
        chHandler.addCHConfig(truckConfig);
        chHandler.addPreparation(PrepareContractionHierarchies.fromGraphHopperStorage(storage, simpleTruckConfig));
        chHandler.addPreparation(PrepareContractionHierarchies.fromGraphHopperStorage(storage, truckConfig));

        assertEquals("fastest|truck", chHandler.getPreparation("truck").getCHConfig().getWeighting().toString());
        assertEquals("fastest|simple_truck", chHandler.getPreparation("simple_truck").getCHConfig().getWeighting().toString());

        // make sure weighting cannot be mixed
        chHandler.addCHConfig(truckConfig);
        chHandler.addCHConfig(simpleTruckConfig);
        try {
            chHandler.addPreparation(PrepareContractionHierarchies.fromGraphHopperStorage(storage, simpleTruckConfig));
            fail();
        } catch (Exception ex) {
        }
    }

    @Test
    public void testGetMultipleWeightingsForCH() {
        GraphHopper hopper = new GraphHopper().
                setProfiles(
                        new Profile("profile1").setVehicle("car").setWeighting("fastest"),
                        new Profile("profile2").setVehicle("car").setWeighting("shortest")
                ).
                setStoreOnFlush(false).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        hopper.getCHPreparationHandler().setCHProfiles(
                new CHProfile("profile1"), new CHProfile("profile2")
        );
        hopper.importOrLoad();
        assertEquals(2, hopper.getCHPreparationHandler().getPreparations().size());
        for (PrepareContractionHierarchies p : hopper.getCHPreparationHandler().getPreparations()) {
            assertTrue(p.isPrepared(), "did not get prepared");
        }
    }

    @Test
    public void testLoadingLMAndCHProfiles() {
        GraphHopper hopper = new GraphHopper()
                .setGraphHopperLocation(ghLoc)
                .setOSMFile(testOsm)
                .setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest"));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("car"));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
        hopper.importOrLoad();
        hopper.close();

        // load without problem
        hopper = new GraphHopper()
                .setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest"));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("car"));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
        assertTrue(hopper.load(ghLoc));
        hopper.close();

        // problem: changed weighting in profile although LM preparation was enabled
        hopper = new GraphHopper()
                .setProfiles(new Profile("car").setVehicle("car").setWeighting("shortest"));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("car"));
        // do not load CH
        try {
            assertFalse(hopper.load(ghLoc));
            fail("load should fail");
        } catch (Exception ex) {
            assertEquals("LM preparation of car already exists in storage and doesn't match configuration", ex.getMessage());
        } finally {
            hopper.close();
        }

        // problem: changed weighting in profile although CH preparation was enabled
        hopper = new GraphHopper()
                .setProfiles(new Profile("car").setVehicle("car").setWeighting("shortest"));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
        // do not load LM
        try {
            assertFalse(hopper.load(ghLoc));
            fail("load should fail");
        } catch (Exception ex) {
            assertEquals("CH preparation of car already exists in storage and doesn't match configuration", ex.getMessage());
        } finally {
            hopper.close();
        }
    }

    @Test
    public void testLoadingCustomProfiles() {
        CustomModel customModel = new CustomModel().setDistanceInfluence(123);
        GraphHopper hopper = new GraphHopper()
                .setGraphHopperLocation(ghLoc)
                .setOSMFile(testOsm)
                .setProfiles(new CustomProfile("car").setCustomModel(customModel));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("car"));
        hopper.importOrLoad();
        hopper.close();

        // load without problem
        hopper = new GraphHopper()
                .setProfiles(new CustomProfile("car").setCustomModel(customModel));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("car"));
        assertTrue(hopper.load(ghLoc));
        hopper.close();

        // do not load changed CustomModel
        customModel.setDistanceInfluence(100);
        hopper = new GraphHopper()
                .setProfiles(new CustomProfile("car").setCustomModel(customModel));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("car"));
        try {
            assertFalse(hopper.load(ghLoc));
            fail("load should fail");
        } catch (Exception ex) {
            assertEquals("LM preparation of car already exists in storage and doesn't match configuration", ex.getMessage());
        } finally {
            hopper.close();
        }
    }


}
