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

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.*;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.config.CHProfileConfig;
import com.graphhopper.config.LMProfileConfig;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.reader.DataReader;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ch.CHPreparationHandler;
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHProfile;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.*;
import com.graphhopper.util.Parameters.Routing;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperOSMTest {
    private static final String ghLoc = "./target/tmp/ghosm";
    private static final String testOsm = "./src/test/resources/com/graphhopper/reader/osm/test-osm.xml";
    private static final String testOsm3 = "./src/test/resources/com/graphhopper/reader/osm/test-osm3.xml";
    private GraphHopper instance;

    @Before
    public void setUp() {
        Helper.removeDir(new File(ghLoc));
    }

    @After
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
        GraphHopper hopper = createGraphHopper(vehicle).
                setStoreOnFlush(true).
                setProfiles(new ProfileConfig(profile).setVehicle(vehicle).setWeighting(weighting)).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm);
        hopper.getCHPreparationHandler().setCHProfileConfigs(new CHProfileConfig(profile));
        hopper.importOrLoad();
        GHResponse rsp = hopper.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).
                setProfile(profile));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().getSize());

        hopper.close();

        // no encoding manager necessary
        hopper = new GraphHopperOSM().
                setProfiles(new ProfileConfig(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true);
        hopper.getCHPreparationHandler().setCHProfileConfigs(new CHProfileConfig(profile));
        assertTrue(hopper.load(ghLoc));
        rsp = hopper.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).
                setProfile(profile));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().getSize());

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
        GraphHopper gh = createGraphHopper(vehicle).
                setProfiles(new ProfileConfig(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm);
        gh.importOrLoad();

        assertFalse(gh.getAlgorithmFactory("profile", false, false) instanceof CHRoutingAlgorithmFactory);

        GHResponse rsp = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4)
                .setProfile(profile));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().getSize());

        gh.close();
        gh = createGraphHopper(vehicle).
                setProfiles(new ProfileConfig(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true);
        assertTrue(gh.load(ghLoc));
        rsp = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4)
                .setProfile(profile));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().getSize());

        gh.close();

        gh = createGraphHopper(vehicle).
                setProfiles(new ProfileConfig(profile).setVehicle(vehicle).setWeighting(weighting)).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm);

        assertFalse(gh.getAlgorithmFactory("profile", false, false) instanceof CHRoutingAlgorithmFactory);
        gh.close();
    }

    @Test
    public void testQueryLocationIndexWithBBox() {
        final GraphHopper gh = createGraphHopper("car").
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile("../core/files/monaco.osm.gz");
        gh.importOrLoad();

        final NodeAccess na = gh.getGraphHopperStorage().getNodeAccess();
        final Collection<Integer> indexNodeList = new TreeSet<>();
        LocationIndexTree index = (LocationIndexTree) gh.getLocationIndex();
        final EdgeExplorer edgeExplorer = gh.getGraphHopperStorage().createEdgeExplorer();
        final BBox bbox = new BBox(7.422, 7.429, 43.729, 43.734);
        index.query(bbox, new LocationIndexTree.EdgeVisitor(edgeExplorer) {
            @Override
            public void onTile(BBox bbox, int width) {
            }

            @Override
            public void onEdge(EdgeIteratorState edge, int nodeA, int nodeB) {
                for (int i = 0; i < 2; i++) {
                    int nodeId = i == 0 ? nodeA : nodeB;
                    double lat = na.getLatitude(nodeId);
                    double lon = na.getLongitude(nodeId);
                    if (bbox.contains(lat, lon))
                        indexNodeList.add(nodeId);
                }
            }
        });

        assertEquals(57, indexNodeList.size());
        for (int nodeId : indexNodeList) {
            if (!bbox.contains(na.getLatitude(nodeId), na.getLongitude(nodeId)))
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
                double lat = na.getLatitude(nodeId);
                double lon = na.getLongitude(nodeId);
                if (bbox.contains(lat, lon))
                    bfsNodeList.add(nodeId);

                return true;
            }
        }.start(edgeExplorer, index.findClosest(43.731, 7.425, EdgeFilter.ALL_EDGES).getClosestNode());

        assertTrue("index size: " + indexNodeList.size() + ", bfs size: " + bfsNodeList.size(), indexNodeList.size() >= bfsNodeList.size());
        assertTrue("index size: " + indexNodeList.size() + ", bfs size: " + bfsNodeList.size(), indexNodeList.containsAll(bfsNodeList));
    }

    @Test
    public void testLoadingWithDifferentCHConfig_issue471_pr1488() {
        // when there is a single CH profile we can also load GraphHopper without it
        // in #471 this was forbidden, but later it was allowed again, see #1488
        final String profile = "profile";
        final String vehicle = "car";
        final String weighting = "fastest";
        GraphHopper gh = createGraphHopper(vehicle).
                setStoreOnFlush(true).
                setProfiles(Collections.singletonList(new ProfileConfig(profile).setVehicle(vehicle).setWeighting(weighting))).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm);
        gh.getCHPreparationHandler().setCHProfileConfigs(new CHProfileConfig(profile));
        gh.importOrLoad();
        GHResponse rsp = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).setProfile(profile));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().getSize());
        gh.close();

        // now load GH without CH profile
        gh = createGraphHopper(vehicle).
                setStoreOnFlush(true);
        gh.load(ghLoc);
        // no error

        Helper.removeDir(new File(ghLoc));

        // when there is no CH preparation we get an error if we try to load GH with a CH profile
        gh = createGraphHopper(vehicle).
                setStoreOnFlush(true).
                setProfiles(Collections.singletonList(new ProfileConfig(profile).setVehicle(vehicle).setWeighting(weighting))).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm);
        gh.importOrLoad();
        rsp = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).setProfile(profile));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().getSize());
        gh.close();

        gh = createGraphHopper("car").
                setProfiles(new ProfileConfig(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true);
        gh.getCHPreparationHandler().setCHProfileConfigs(new CHProfileConfig("profile"));

        try {
            gh.load(ghLoc);
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().contains("is not contained in loaded CH profiles"));
        }
    }

    @Test
    public void testAllowMultipleReadingInstances() {
        String vehicle = "car";
        GraphHopper instance1 = createGraphHopper(vehicle).
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm);
        instance1.importOrLoad();

        GraphHopper instance2 = createGraphHopper(vehicle).
                setStoreOnFlush(true).
                setDataReaderFile(testOsm);
        instance2.load(ghLoc);

        GraphHopper instance3 = createGraphHopper(vehicle).
                setStoreOnFlush(true).
                setDataReaderFile(testOsm);
        instance3.load(ghLoc);

        instance1.close();
        instance2.close();
        instance3.close();
    }

    @Test
    public void testDoNotAllowWritingAndLoadingAtTheSameTime() throws Exception {
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        final GraphHopper instance1 = new GraphHopperOSM() {
            @Override
            protected DataReader importData() throws IOException {
                try {
                    latch2.countDown();
                    latch1.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                return super.importData();
            }
        }.setStoreOnFlush(true).
                setEncodingManager(EncodingManager.create("car")).
                setGraphHopperLocation(ghLoc).
                setProfiles(new ProfileConfig("car").setVehicle("car").setWeighting("fastest")).
                setDataReaderFile(testOsm);
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

        GraphHopper instance2 = createGraphHopper("car").
                setStoreOnFlush(true).
                setDataReaderFile(testOsm);
        try {
            // let thread reach the CountDownLatch
            latch2.await(3, TimeUnit.SECONDS);
            // now importOrLoad should have create a lock which this load call does not like
            instance2.load(ghLoc);
            fail("There should have been an error because of the lock");
        } catch (RuntimeException ex) {
            assertNotNull(ex);
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("To avoid reading partial data"));
        } finally {
            instance2.close();
            latch1.countDown();
            // make sure the import process wasn't interrupted and no other error happened
            thread.join();
        }

        if (ar.get() != null)
            assertNull(ar.get().getMessage(), ar.get());
        instance1.close();
    }

    @Test
    public void testPrepare() {
        final String profile = "profile";
        final String vehicle = "car";
        final String weighting = "shortest";

        instance = createGraphHopper(vehicle).
                setStoreOnFlush(false).
                setProfiles(Collections.singletonList(new ProfileConfig(profile).setVehicle(vehicle).setWeighting(weighting))).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm);
        instance.getCHPreparationHandler().setCHProfileConfigs(new CHProfileConfig(profile));
        instance.importOrLoad();
        GHResponse rsp = instance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).
                setProfile(profile).
                setAlgorithm(DIJKSTRA_BI));
        assertFalse(rsp.hasErrors());
        assertEquals(Helper.createPointList(51.249215, 9.431716, 52.0, 9.0, 51.2, 9.4), rsp.getBest().getPoints());
        assertEquals(3, rsp.getBest().getPoints().getSize());
    }

    @Test
    public void testSortedGraph_noCH() {
        final String profile = "profile";
        final String vehicle = "car";
        final String weighting = "fastest";
        instance = createGraphHopper(vehicle).
                setProfiles(new ProfileConfig(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(false).
                setSortGraph(true).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm);
        instance.importOrLoad();
        PathWrapper rsp = instance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).
                setProfile(profile).
                setAlgorithm(DIJKSTRA_BI)).getBest();
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getPoints().getSize());
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
        assertEquals("route method should not change instance field", old, instance.getEncodingManager().isEnableInstructions());
    }

    @Test
    public void testFootAndCar() {
        final String profile1 = "profile1";
        final String profile2 = "profile2";
        final String vehicle1 = "car";
        final String vehicle2 = "foot";
        final String weighting = "fastest";

        // now all ways are imported
        instance = createGraphHopper(vehicle1 + "," + vehicle2).
                setProfiles(
                        new ProfileConfig(profile1).setVehicle(vehicle1).setWeighting(weighting),
                        new ProfileConfig(profile2).setVehicle(vehicle2).setWeighting(weighting)
                ).
                setStoreOnFlush(false).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm3);
        instance.importOrLoad();

        assertEquals(5, instance.getGraphHopperStorage().getNodes());
        assertEquals(8, instance.getGraphHopperStorage().getEdges());

        // A to D
        GHResponse grsp = instance.route(new GHRequest(11.1, 50, 11.3, 51).setProfile(profile1));
        assertFalse(grsp.hasErrors());
        PathWrapper rsp = grsp.getBest();
        assertEquals(3, rsp.getPoints().getSize());
        // => found A and D
        assertEquals(50, rsp.getPoints().getLongitude(0), 1e-3);
        assertEquals(11.1, rsp.getPoints().getLatitude(0), 1e-3);
        assertEquals(51, rsp.getPoints().getLongitude(2), 1e-3);
        assertEquals(11.3, rsp.getPoints().getLatitude(2), 1e-3);

        // A to D not allowed for foot. But the location index will choose a node close to D accessible to FOOT
        grsp = instance.route(new GHRequest(11.1, 50, 11.3, 51).setProfile(profile2));
        assertFalse(grsp.hasErrors());
        rsp = grsp.getBest();
        assertEquals(2, rsp.getPoints().getSize());
        // => found a point on edge A-B        
        assertEquals(11.680, rsp.getPoints().getLatitude(1), 1e-3);
        assertEquals(50.644, rsp.getPoints().getLongitude(1), 1e-3);

        // A to E only for foot
        grsp = instance.route(new GHRequest(11.1, 50, 10, 51).setProfile(profile2));
        assertFalse(grsp.hasErrors());
        rsp = grsp.getBest();
        assertEquals(2, rsp.getPoints().size());

        // A D E for car
        grsp = instance.route(new GHRequest(11.1, 50, 10, 51).setProfile(profile1));
        assertFalse(grsp.hasErrors());
        rsp = grsp.getBest();
        assertEquals(3, rsp.getPoints().getSize());
    }

    @Test
    public void testFailsForWrongConfig() {
        instance = new GraphHopperOSM().init(
                new GraphHopperConfig().
                        putObject("datareader.file", testOsm3).
                        putObject("datareader.dataaccess", "RAM").
                        putObject("graph.flag_encoders", "foot,car")).
                setGraphHopperLocation(ghLoc).
                setProfiles(Arrays.asList(
                        new ProfileConfig("foot").setVehicle("foot").setWeighting("fastest"),
                        new ProfileConfig("car").setVehicle("car").setWeighting("fastest")
                ));
        instance.importOrLoad();
        assertEquals(5, instance.getGraphHopperStorage().getNodes());
        instance.close();

        // different config (flagEncoder list)
        try {
            GraphHopper tmpGH = new GraphHopperOSM().init(
                    new GraphHopperConfig().
                            putObject("datareader.file", testOsm3).
                            putObject("datareader.dataaccess", "RAM").
                            putObject("graph.flag_encoders", "foot")).
                    setDataReaderFile(testOsm3).
                    setProfiles(Collections.singletonList(
                            new ProfileConfig("foot").setVehicle("foot").setWeighting("fastest")
                    ));
            tmpGH.load(ghLoc);
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Encoding does not match"));
        }

        // different order is no longer okay, see #350
        try {
            GraphHopper tmpGH = new GraphHopperOSM().init(new GraphHopperConfig().
                    putObject("datareader.file", testOsm3).
                    putObject("datareader.dataaccess", "RAM").
                    putObject("graph.flag_encoders", "car,foot")).
                    setDataReaderFile(testOsm3).
                    setProfiles(Arrays.asList(
                            new ProfileConfig("car").setVehicle("car").setWeighting("fastest"),
                            new ProfileConfig("foot").setVehicle("foot").setWeighting("fastest")
                    ));
            tmpGH.load(ghLoc);
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Encoding does not match"));
        }

        // different encoded values should fail to load
        instance = new GraphHopperOSM().init(
                new GraphHopperConfig().
                        putObject("datareader.file", testOsm3).
                        putObject("datareader.dataaccess", "RAM").
                        putObject("graph.encoded_values", "road_class").
                        putObject("graph.flag_encoders", "foot,car")).
                setDataReaderFile(testOsm3).
                setProfiles(Arrays.asList(
                        new ProfileConfig("foot").setVehicle("foot").setWeighting("fastest"),
                        new ProfileConfig("car").setVehicle("car").setWeighting("fastest")
                ));
        try {
            instance.load(ghLoc);
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Encoded values do not match"));
        }

        // different version for car should fail
        instance = new GraphHopperOSM().setEncodingManager(EncodingManager.create(new FootFlagEncoder(), new CarFlagEncoder() {
            @Override
            public int getVersion() {
                return 0;
            }
        })).init(new GraphHopperConfig().
                putObject("datareader.file", testOsm3).
                putObject("datareader.dataaccess", "RAM")).
                setDataReaderFile(testOsm3).
                setProfiles(Collections.singletonList(
                        new ProfileConfig("car").setVehicle("car").setWeighting("fastest")
                ));
        try {
            instance.load(ghLoc);
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Encoding does not match"));
        }
    }

    @Test
    public void testFailsForWrongEVConfig() {
        instance = new GraphHopperOSM().init(
                new GraphHopperConfig().
                        putObject("datareader.file", testOsm3).
                        putObject("datareader.dataaccess", "RAM").
                        putObject("graph.flag_encoders", "foot,car")).
                setProfiles(Arrays.asList(
                        new ProfileConfig("foot").setVehicle("foot").setWeighting("fastest"),
                        new ProfileConfig("car").setVehicle("car").setWeighting("fastest")
                )).
                setGraphHopperLocation(ghLoc);
        instance.importOrLoad();
        // older versions <= 0.12 did not store this property, ensure that we fail to load it
        instance.getGraphHopperStorage().getProperties().remove("graph.encoded_values");
        instance.getGraphHopperStorage().flush();
        assertEquals(5, instance.getGraphHopperStorage().getNodes());
        instance.close();

        // different encoded values should fail to load
        instance = new GraphHopperOSM().init(
                new GraphHopperConfig().
                        putObject("datareader.file", testOsm3).
                        putObject("datareader.dataaccess", "RAM").
                        putObject("graph.encoded_values", "road_environment,road_class").
                        putObject("graph.flag_encoders", "foot,car")).
                setProfiles(Arrays.asList(
                        new ProfileConfig("foot").setVehicle("foot").setWeighting("fastest"),
                        new ProfileConfig("car").setVehicle("car").setWeighting("fastest")
                )).
                setDataReaderFile(testOsm3);
        try {
            instance.load(ghLoc);
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Encoded values do not match"));
        }
    }

    @Test
    public void testNoNPE_ifLoadNotSuccessful() {
        String profile = "profile";
        String vehicle = "car";
        String weighting = "fastest";
        instance = createGraphHopper(vehicle).
                setProfiles(new ProfileConfig(profile).setVehicle(vehicle).setWeighting(weighting)).
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
        instance = createGraphHopper("car");
        assertFalse(instance.load(ghLoc));
        assertFalse(new File(ghLoc).exists());
    }

    @Test
    public void testFailsForMissingParameters() throws IOException {
        class GHTmp extends GraphHopperOSM {
            @Override
            public DataReader importData() throws IOException {
                return super.importData();
            }
        }

        // missing load of graph
        GHTmp tmp = new GHTmp();
        try {
            tmp.setDataReaderFile(testOsm);
            tmp.importData();
            fail();
        } catch (IllegalStateException ex) {
            assertEquals("Load graph before importing OSM data", ex.getMessage());
        }

        // missing graph location
        instance = new GraphHopperOSM();
        try {
            instance.importOrLoad();
            fail();
        } catch (IllegalStateException ex) {
            assertEquals("GraphHopperLocation is not specified. Call setGraphHopperLocation or init before", ex.getMessage());
        }

        // missing OSM file to import
        instance = createGraphHopper("car").
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
        instance = new GraphHopperOSM().
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm3);
        try {
            instance.importOrLoad();
            fail();
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Cannot load properties to fetch EncodingManager"));
        }

        // Import is possible even if no storeOnFlush is specified BUT here we miss the OSM file
        instance = createGraphHopper("car").
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
        instance = createGraphHopper(vehicle).
                setStoreOnFlush(false).
                setProfiles(new ProfileConfig(profile).setVehicle(vehicle).setWeighting(weighting)).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm3);
        instance.getCHPreparationHandler().setCHProfileConfigs(new CHProfileConfig(profile));
        instance.importOrLoad();

        assertEquals(2, instance.getGraphHopperStorage().getNodes());
        assertEquals(2, instance.getGraphHopperStorage().getAllEdges().length());

        // A to E only for foot
        GHResponse grsp = instance.route(new GHRequest(11.1, 50, 11.19, 52).setProfile(profile));
        assertFalse(grsp.hasErrors());
        PathWrapper rsp = grsp.getBest();
        // the last points snaps to the edge
        assertEquals(Helper.createPointList(11.1, 50, 10, 51, 11.194015, 51.995013), rsp.getPoints());
    }

    @Test
    public void testVia() {
        final String profile = "profile";
        final String vehicle = "car";
        final String weighting = "fastest";
        instance = new GraphHopperOSM().setStoreOnFlush(true).
                init(new GraphHopperConfig().
                        putObject("datareader.file", testOsm3).
                        putObject("prepare.min_network_size", 1).
                        putObject("graph.flag_encoders", vehicle)
                        .setProfiles(Collections.singletonList(new ProfileConfig(profile).setVehicle(vehicle).setWeighting(weighting)))
                        .setCHProfiles(Collections.singletonList(new CHProfileConfig(profile)))
                ).
                setGraphHopperLocation(ghLoc);
        instance.importOrLoad();

        // A -> B -> C
        GHPoint first = new GHPoint(11.1, 50);
        GHPoint second = new GHPoint(12, 51);
        GHPoint third = new GHPoint(11.2, 51.9);
        GHResponse rsp12 = instance.route(new GHRequest(first, second).setProfile(profile));
        assertFalse("should find 1->2", rsp12.hasErrors());
        assertEquals(147930.5, rsp12.getBest().getDistance(), .1);
        GHResponse rsp23 = instance.route(new GHRequest(second, third).setProfile(profile));
        assertFalse("should find 2->3", rsp23.hasErrors());
        assertEquals(176608.9, rsp23.getBest().getDistance(), .1);

        GHResponse grsp = instance.route(new GHRequest(Arrays.asList(first, second, third)).setProfile(profile));
        assertFalse("should find 1->2->3", grsp.hasErrors());
        PathWrapper rsp = grsp.getBest();
        assertEquals(rsp12.getBest().getDistance() + rsp23.getBest().getDistance(), rsp.getDistance(), 1e-6);
        assertEquals(4, rsp.getPoints().getSize());
        assertEquals(5, rsp.getInstructions().size());
        assertEquals(Instruction.REACHED_VIA, rsp.getInstructions().get(1).getSign());
    }

    @Test
    public void testGetPathsDirectionEnforcement1() {
        // Test enforce start direction
        // Note: This Test does not pass for CH enabled    
        instance = createSquareGraphInstance();

        // Start in middle of edge 4-5 
        GHPoint start = new GHPoint(0.0015, 0.002);
        // End at middle of edge 2-3
        GHPoint end = new GHPoint(0.002, 0.0005);

        GHRequest req = new GHRequest().
                setPoints(Arrays.asList(start, end)).
                setHeadings(Arrays.asList(180., Double.NaN)).
                setProfile("profile");
        GHResponse response = new GHResponse();
        List<Path> paths = instance.calcPaths(req, response);
        assertFalse(response.hasErrors());
        assertArrayEquals(new int[]{9, 5, 8, 3, 10}, paths.get(0).calcNodes().toArray());
    }

    @Test
    public void testGetPathsDirectionEnforcement2() {
        // Test enforce south start direction and east end direction
        instance = createSquareGraphInstance();

        // Start in middle of edge 4-5 
        GHPoint start = new GHPoint(0.0015, 0.002);
        // End at middle of edge 2-3
        GHPoint end = new GHPoint(0.002, 0.0005);

        GHRequest req = new GHRequest(start, end).
                setHeadings(Arrays.asList(180.0, 90.0)).
                setProfile("profile");
        GHResponse response = new GHResponse();
        List<Path> paths = instance.calcPaths(req, response);
        assertFalse(response.hasErrors());
        assertArrayEquals(new int[]{9, 5, 8, 1, 2, 10}, paths.get(0).calcNodes().toArray());

        // Test uni-directional case
        req.setAlgorithm(DIJKSTRA);
        response = new GHResponse();
        paths = instance.calcPaths(req, response);
        assertFalse(response.getErrors().toString(), response.hasErrors());
        assertArrayEquals(new int[]{9, 5, 8, 1, 2, 10}, paths.get(0).calcNodes().toArray());
    }

    @Test
    public void testGetPathsDirectionEnforcement3() {
        instance = createSquareGraphInstance();

        // Start in middle of edge 4-5 
        GHPoint start = new GHPoint(0.0015, 0.002);
        // End at middle of edge 2-3
        GHPoint end = new GHPoint(0.002, 0.0005);
        // Via Point betweeen 8-7
        GHPoint via = new GHPoint(0.0005, 0.001);

        GHRequest req = new GHRequest().
                setPoints(Arrays.asList(start, via, end)).
                setHeadings(Arrays.asList(Double.NaN, 0., Double.NaN)).
                setProfile("profile");
        GHResponse response = new GHResponse();
        List<Path> paths = instance.calcPaths(req, response);
        assertFalse(response.hasErrors());
        assertEquals(IntArrayList.from(9, 5, 6, 7, 11), paths.get(0).calcNodes());
    }

    @Test
    public void testGetPathsDirectionEnforcement4() {
        // Test straight via routing
        instance = createSquareGraphInstance();

        // Start in middle of edge 4-5 
        GHPoint start = new GHPoint(0.0015, 0.002);
        // End at middle of edge 2-3
        GHPoint end = new GHPoint(0.002, 0.0005);
        // Via Point betweeen 8-3
        GHPoint via = new GHPoint(0.0015, 0.001);
        GHRequest req = new GHRequest().
                setPoints(Arrays.asList(start, via, end)).
                setProfile("profile");
        req.putHint(Routing.PASS_THROUGH, true);
        GHResponse response = new GHResponse();
        List<Path> paths = instance.calcPaths(req, response);
        assertFalse(response.hasErrors());
        assertEquals(1, response.getAll().size());
        assertEquals(IntArrayList.from(9, 4, 3, 10), paths.get(0).calcNodes());
        assertEquals(IntArrayList.from(10, 8, 1, 2, 11), paths.get(1).calcNodes());
    }

    @Test
    public void testGetPathsDirectionEnforcement5() {
        // Test independence of previous enforcement for subsequent pathes
        instance = createSquareGraphInstance();

        // Start in middle of edge 4-5 
        GHPoint start = new GHPoint(0.0015, 0.002);
        // End at middle of edge 2-3
        GHPoint end = new GHPoint(0.002, 0.0005);
        // First go south and than come from west to via-point at 7-6. Then go back over previously punished (11)-4 edge
        GHPoint via = new GHPoint(0.000, 0.0015);
        GHRequest req = new GHRequest().
                setPoints(Arrays.asList(start, via, end)).
                setHeadings(Arrays.asList(0., 3.14 / 2, Double.NaN)).
                setProfile("profile");
        req.putHint(Routing.PASS_THROUGH, true);
        GHResponse response = new GHResponse();
        List<Path> paths = instance.calcPaths(req, response);
        assertFalse(response.hasErrors());
        assertEquals(IntArrayList.from(9, 4, 3, 8, 7, 11), paths.get(0).calcNodes());
        assertEquals(IntArrayList.from(11, 6, 5, 9, 4, 3, 10), paths.get(1).calcNodes());
    }

    @Test
    public void testGetPathsDirectionEnforcement6() {
        // Test if query results at tower nodes are ignored
        instance = createSquareGraphInstance();

        // QueryPoints directly on TowerNodes 
        GHPoint start = new GHPoint(0, 0);
        GHPoint via = new GHPoint(0.002, 0.000);
        GHPoint end = new GHPoint(0.002, 0.002);

        GHRequest req = new GHRequest().
                setPoints(Arrays.asList(start, via, end)).
                setHeadings(Arrays.asList(90., 270., 270.)).
                setProfile("profile");
        GHResponse response = new GHResponse();
        List<Path> paths = instance.calcPaths(req, response);
        assertFalse(response.hasErrors());
        assertArrayEquals(new int[]{0, 1, 2}, paths.get(0).calcNodes().toArray());
        assertArrayEquals(new int[]{2, 3, 4}, paths.get(1).calcNodes().toArray());
    }

    private GraphHopper createSquareGraphInstance() {
        CarFlagEncoder carEncoder = new CarFlagEncoder();
        EncodingManager encodingManager = EncodingManager.create(carEncoder);
        Weighting weighting = new FastestWeighting(carEncoder);
        GraphHopperStorage g = new GraphBuilder(encodingManager).setCHProfiles(CHProfile.nodeBased(weighting)).setBytes(20).create();

        //   2---3---4
        //  /    |    \
        //  1----8----5
        //  /    |    /
        //  0----7---6
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0.000, 0.000);
        na.setNode(1, 0.001, 0.000);
        na.setNode(2, 0.002, 0.000);
        na.setNode(3, 0.002, 0.001);
        na.setNode(4, 0.002, 0.002);
        na.setNode(5, 0.001, 0.002);
        na.setNode(6, 0.000, 0.002);
        na.setNode(7, 0.000, 0.001);
        na.setNode(8, 0.001, 0.001);

        g.edge(0, 1, 100, true);
        g.edge(1, 2, 100, true);
        g.edge(2, 3, 100, true);
        g.edge(3, 4, 100, true);
        g.edge(4, 5, 100, true);
        g.edge(5, 6, 100, true);
        g.edge(6, 7, 100, true);
        g.edge(7, 0, 100, true);

        g.edge(1, 8, 110, true);
        g.edge(3, 8, 110, true);
        g.edge(5, 8, 110, true);
        g.edge(7, 8, 110, true);

        GraphHopper tmp = new GraphHopperOSM().
                setEncodingManager(encodingManager).
                setProfiles(new ProfileConfig("profile").setVehicle("car").setWeighting("fastest"));
        tmp.setGraphHopperStorage(g);
        tmp.postProcessing();

        return tmp;
    }

    @Test
    public void testMultipleCHPreparationsInParallel() {
        HashMap<String, Long> shortcutCountMap = new HashMap<>();
        // try all parallelization modes        
        for (int threadCount = 1; threadCount < 6; threadCount++) {
            EncodingManager em = EncodingManager.create(Arrays.asList(
                    new CarFlagEncoder(),
                    new MotorcycleFlagEncoder(),
                    new MountainBikeFlagEncoder(),
                    new RacingBikeFlagEncoder(),
                    new FootFlagEncoder()));

            GraphHopper hopper = new GraphHopperOSM().
                    setStoreOnFlush(false).
                    setEncodingManager(em).
                    setProfiles(
                            new ProfileConfig("car_profile").setVehicle("car").setWeighting("fastest"),
                            new ProfileConfig("moto_profile").setVehicle("motorcycle").setWeighting("fastest"),
                            new ProfileConfig("mtb_profile").setVehicle("mtb").setWeighting("fastest"),
                            new ProfileConfig("bike_profile").setVehicle("racingbike").setWeighting("fastest"),
                            new ProfileConfig("foot_profile").setVehicle("foot").setWeighting("fastest")
                    ).
                    setGraphHopperLocation(ghLoc).
                    setDataReaderFile(testOsm);
            hopper.getCHPreparationHandler()
                    .setCHProfileConfigs(
                            new CHProfileConfig("car_profile"),
                            new CHProfileConfig("moto_profile"),
                            new CHProfileConfig("mtb_profile"),
                            new CHProfileConfig("bike_profile"),
                            new CHProfileConfig("foot_profile")
                    )
                    .setPreparationThreads(threadCount);

            hopper.importOrLoad();

            assertEquals(5, hopper.getCHPreparationHandler().getPreparations().size());
            for (PrepareContractionHierarchies pch : hopper.getCHPreparationHandler().getPreparations()) {
                assertTrue("Preparation wasn't run! [" + threadCount + "]", pch.isPrepared());

                String name = pch.getCHProfile().toFileName();
                Long singleThreadShortcutCount = shortcutCountMap.get(name);
                if (singleThreadShortcutCount == null)
                    shortcutCountMap.put(name, pch.getShortcuts());
                else
                    assertEquals((long) singleThreadShortcutCount, pch.getShortcuts());

                String keyError = Parameters.CH.PREPARE + "error." + name;
                String valueError = hopper.getGraphHopperStorage().getProperties().get(keyError);
                assertTrue("Properties for " + name + " should NOT contain error " + valueError + " [" + threadCount + "]", valueError.isEmpty());

                String key = Parameters.CH.PREPARE + "date." + name;
                String value = hopper.getGraphHopperStorage().getProperties().get(key);
                assertFalse("Properties for " + name + " did NOT contain finish date [" + threadCount + "]", value.isEmpty());
            }
            hopper.close();
        }
    }

    @Test
    public void testMultipleLMPreparationsInParallel() {
        HashMap<String, Integer> landmarkCount = new HashMap<>();
        // try all parallelization modes
        for (int threadCount = 1; threadCount < 6; threadCount++) {
            EncodingManager em = EncodingManager.create(Arrays.asList(
                    new CarFlagEncoder(),
                    new MotorcycleFlagEncoder(),
                    new MountainBikeFlagEncoder(),
                    new RacingBikeFlagEncoder(),
                    new FootFlagEncoder()));

            GraphHopper hopper = new GraphHopperOSM().
                    setStoreOnFlush(false).
                    setEncodingManager(em).
                    setProfiles(Arrays.asList(
                            new ProfileConfig("car_profile").setVehicle("car").setWeighting("fastest"),
                            new ProfileConfig("moto_profile").setVehicle("motorcycle").setWeighting("fastest"),
                            new ProfileConfig("mtb_profile").setVehicle("mtb").setWeighting("fastest"),
                            new ProfileConfig("bike_profile").setVehicle("racingbike").setWeighting("fastest"),
                            new ProfileConfig("foot_profile").setVehicle("foot").setWeighting("fastest")
                    )).
                    setGraphHopperLocation(ghLoc).
                    setDataReaderFile(testOsm);
            hopper.getLMPreparationHandler().
                    setLMProfileConfigs(
                            new LMProfileConfig("car_profile"),
                            new LMProfileConfig("moto_profile"),
                            new LMProfileConfig("mtb_profile"),
                            new LMProfileConfig("bike_profile"),
                            new LMProfileConfig("foot_profile")
                    ).
                    setPreparationThreads(threadCount);

            hopper.importOrLoad();

            assertEquals(5, hopper.getLMPreparationHandler().getPreparations().size());
            for (PrepareLandmarks prepLM : hopper.getLMPreparationHandler().getPreparations()) {
                assertTrue("Preparation wasn't run! [" + threadCount + "]", prepLM.isPrepared());

                String name = prepLM.getLMProfile().getName();
                Integer singleThreadShortcutCount = landmarkCount.get(name);
                if (singleThreadShortcutCount == null)
                    landmarkCount.put(name, prepLM.getSubnetworksWithLandmarks());
                else
                    assertEquals((int) singleThreadShortcutCount, prepLM.getSubnetworksWithLandmarks());

                String keyError = Parameters.Landmark.PREPARE + "error." + name;
                String valueError = hopper.getGraphHopperStorage().getProperties().get(keyError);
                assertTrue("Properties for " + name + " should NOT contain error " + valueError + " [" + threadCount + "]", valueError.isEmpty());

                String key = Parameters.Landmark.PREPARE + "date." + name;
                String value = hopper.getGraphHopperStorage().getProperties().get(key);
                assertFalse("Properties for " + name + " did NOT contain finish date [" + threadCount + "]", value.isEmpty());
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
        CHProfile simpleTruckProfile = CHProfile.nodeBased("simple_truck", fwSimpleTruck);
        CHProfile truckProfile = CHProfile.nodeBased("truck", fwTruck);
        GraphHopperStorage storage = new GraphBuilder(em).setCHProfiles(Arrays.asList(simpleTruckProfile, truckProfile)).build();
        chHandler.addCHProfile(simpleTruckProfile);
        chHandler.addCHProfile(truckProfile);
        chHandler.addPreparation(PrepareContractionHierarchies.fromGraphHopperStorage(storage, simpleTruckProfile));
        chHandler.addPreparation(PrepareContractionHierarchies.fromGraphHopperStorage(storage, truckProfile));

        assertEquals("fastest|truck", ((CHRoutingAlgorithmFactory) chHandler.getAlgorithmFactory("truck")).getWeighting().toString());
        assertEquals("fastest|simple_truck", ((CHRoutingAlgorithmFactory) chHandler.getAlgorithmFactory("simple_truck")).getWeighting().toString());

        // make sure weighting cannot be mixed
        chHandler.addCHProfile(truckProfile);
        chHandler.addCHProfile(simpleTruckProfile);
        try {
            chHandler.addPreparation(PrepareContractionHierarchies.fromGraphHopperStorage(storage, simpleTruckProfile));
            fail();
        } catch (Exception ex) {
        }
    }

    @Test
    public void testGetMultipleWeightingsForCH() {
        EncodingManager em = EncodingManager.create("car");
        GraphHopper hopper = new GraphHopperOSM().
                setProfiles(
                        new ProfileConfig("profile1").setVehicle("car").setWeighting("fastest"),
                        new ProfileConfig("profile2").setVehicle("car").setWeighting("shortest")
                ).
                setStoreOnFlush(false).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm).
                setEncodingManager(em);
        hopper.getCHPreparationHandler().setCHProfileConfigs(
                new CHProfileConfig("profile1"), new CHProfileConfig("profile2")
        );
        hopper.importOrLoad();
        assertEquals(2, hopper.getCHPreparationHandler().getPreparations().size());
        for (PrepareContractionHierarchies p : hopper.getCHPreparationHandler().getPreparations()) {
            assertTrue("did not get prepared", p.isPrepared());
        }
    }

    private GraphHopper createGraphHopper(String vehicles) {
        EncodingManager em = EncodingManager.create(vehicles);
        List<ProfileConfig> profiles = new ArrayList<>();
        for (FlagEncoder enc : em.fetchEdgeEncoders()) {
            profiles.add(new ProfileConfig(enc.toString()).setVehicle(enc.toString()).setWeighting("fastest"));
        }
        return new GraphHopperOSM().
                setEncodingManager(em).
                setProfiles(profiles);
    }
}
