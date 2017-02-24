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
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.reader.DataReader;
import com.graphhopper.routing.*;
import com.graphhopper.routing.ch.CHAlgoFactoryDecorator;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.Parameters.Routing;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
        GraphHopper closableInstance = new GraphHopperOSM().
                setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("car")).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm);
        closableInstance.importOrLoad();
        GHResponse rsp = closableInstance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().getSize());

        closableInstance.close();

        // no encoding manager necessary
        closableInstance = new GraphHopperOSM().
                setStoreOnFlush(true);
        assertTrue(closableInstance.load(ghLoc));
        rsp = closableInstance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().getSize());

        closableInstance.close();
        try {
            rsp = closableInstance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4));
            assertTrue(false);
        } catch (Exception ex) {
            assertEquals("You need to create a new GraphHopper instance as it is already closed", ex.getMessage());
        }

        try {
            closableInstance.getLocationIndex().findClosest(51.2492152, 9.4317166, EdgeFilter.ALL_EDGES);
            assertTrue(false);
        } catch (Exception ex) {
            assertEquals("You need to create a new LocationIndex instance as it is already closed", ex.getMessage());
        }
    }

    @Test
    public void testLoadOSMNoCH() {
        GraphHopper gh = new GraphHopperOSM().setStoreOnFlush(true).setCHEnabled(false).
                setEncodingManager(new EncodingManager("car")).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm);
        gh.importOrLoad();

        assertFalse(gh.getAlgorithmFactory(new HintsMap("fastest")) instanceof PrepareContractionHierarchies);

        GHResponse rsp = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().getSize());

        gh.close();
        gh = new GraphHopperOSM().setStoreOnFlush(true).setCHEnabled(false).
                setEncodingManager(new EncodingManager("car"));
        assertTrue(gh.load(ghLoc));
        rsp = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().getSize());

        gh.close();

        gh = new GraphHopperOSM().
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm).
                init(new CmdArgs().put("graph.flag_encoders", "car").put(Parameters.CH.PREPARE + "weightings", "no"));

        assertFalse(gh.getAlgorithmFactory(new HintsMap("fastest")) instanceof PrepareContractionHierarchies);
        gh.close();
    }

    @Test
    public void testLoadingWithDifferentCHConfig_issue471() {
        // with CH should not be loadable without CH configured
        GraphHopper gh = new GraphHopperOSM().setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("car")).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm);
        gh.importOrLoad();
        GHResponse rsp = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().getSize());
        gh.close();

        gh = new GraphHopperOSM().setStoreOnFlush(true).setCHEnabled(false).
                setEncodingManager(new EncodingManager("car"));
        try {
            gh.load(ghLoc);
            assertTrue(false);
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Configured graph.ch.weightings:"));
        }

        Helper.removeDir(new File(ghLoc));

        // without CH should not be loadable with CH enabled
        gh = new GraphHopperOSM().setStoreOnFlush(true).setCHEnabled(false).
                setEncodingManager(new EncodingManager("car")).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm);
        gh.importOrLoad();
        rsp = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4));
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getBest().getPoints().getSize());
        gh.close();

        gh = new GraphHopperOSM().setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("car"));
        try {
            gh.load(ghLoc);
            assertTrue(false);
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Configured graph.ch.weightings:"));
        }
    }

    @Test
    public void testAllowMultipleReadingInstances() {
        GraphHopper instance1 = new GraphHopperOSM().setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("car")).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm);
        instance1.importOrLoad();

        GraphHopper instance2 = new GraphHopperOSM().setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("car")).
                setDataReaderFile(testOsm);
        instance2.load(ghLoc);

        GraphHopper instance3 = new GraphHopperOSM().setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("car")).
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
                setEncodingManager(new EncodingManager("car")).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm);
        final AtomicReference<Exception> ar = new AtomicReference<Exception>();
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

        GraphHopper instance2 = new GraphHopperOSM().setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("car")).
                setDataReaderFile(testOsm);
        try {
            // let thread reach the CountDownLatch
            latch2.await(3, TimeUnit.SECONDS);
            // now importOrLoad should have create a lock which this load call does not like
            instance2.load(ghLoc);
            assertTrue(false);
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
        instance = new GraphHopperOSM().
                setStoreOnFlush(false).
                setEncodingManager(new EncodingManager("car")).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm);
        instance.getCHFactoryDecorator().setWeightingsAsStrings("shortest");
        instance.importOrLoad();
        GHResponse rsp = instance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).
                setAlgorithm(DIJKSTRA_BI));
        assertFalse(rsp.hasErrors());
        assertEquals(Helper.createPointList(51.249215, 9.431716, 52.0, 9.0, 51.2, 9.4), rsp.getBest().getPoints());
        assertEquals(3, rsp.getBest().getPoints().getSize());
    }

    @Test
    public void testSortedGraph_noCH() {
        instance = new GraphHopperOSM().setStoreOnFlush(false).
                setSortGraph(true).
                setEncodingManager(new EncodingManager("car")).setCHEnabled(false).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm);
        instance.importOrLoad();
        PathWrapper rsp = instance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).
                setAlgorithm(DIJKSTRA_BI)).getBest();
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getPoints().getSize());
        assertEquals(new GHPoint(51.24921503475044, 9.431716451757769), rsp.getPoints().toGHPoint(0));
        assertEquals(new GHPoint(52.0, 9.0), rsp.getPoints().toGHPoint(1));
        assertEquals(new GHPoint(51.199999850988384, 9.39999970197677), rsp.getPoints().toGHPoint(2));

        GHRequest req = new GHRequest(51.2492152, 9.4317166, 51.2, 9.4);
        boolean old = instance.isEnableInstructions();
        req.getHints().put("instructions", true);
        instance.route(req);
        assertEquals(old, instance.isEnableInstructions());

        req.getHints().put("instructions", false);
        instance.route(req);
        assertEquals("route method should not change instance field", old, instance.isEnableInstructions());
    }

    @Test
    public void testFootAndCar() {
        // now all ways are imported
        instance = new GraphHopperOSM().setStoreOnFlush(false).
                setEncodingManager(new EncodingManager("car,foot")).setCHEnabled(false).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm3);
        instance.importOrLoad();

        assertEquals(5, instance.getGraphHopperStorage().getNodes());
        assertEquals(8, instance.getGraphHopperStorage().getAllEdges().getMaxId());

        // A to D
        GHResponse grsp = instance.route(new GHRequest(11.1, 50, 11.3, 51).setVehicle("car"));
        assertFalse(grsp.hasErrors());
        PathWrapper rsp = grsp.getBest();
        assertEquals(3, rsp.getPoints().getSize());
        // => found A and D
        assertEquals(50, rsp.getPoints().getLongitude(0), 1e-3);
        assertEquals(11.1, rsp.getPoints().getLatitude(0), 1e-3);
        assertEquals(51, rsp.getPoints().getLongitude(2), 1e-3);
        assertEquals(11.3, rsp.getPoints().getLatitude(2), 1e-3);

        // A to D not allowed for foot. But the location index will choose a node close to D accessible to FOOT        
        grsp = instance.route(new GHRequest(11.1, 50, 11.3, 51).setVehicle("foot"));
        assertFalse(grsp.hasErrors());
        rsp = grsp.getBest();
        assertEquals(2, rsp.getPoints().getSize());
        // => found a point on edge A-B        
        assertEquals(11.680, rsp.getPoints().getLatitude(1), 1e-3);
        assertEquals(50.644, rsp.getPoints().getLongitude(1), 1e-3);

        // A to E only for foot
        grsp = instance.route(new GHRequest(11.1, 50, 10, 51).setVehicle("foot"));
        assertFalse(grsp.hasErrors());
        rsp = grsp.getBest();
        assertEquals(2, rsp.getPoints().size());

        // A D E for car
        grsp = instance.route(new GHRequest(11.1, 50, 10, 51).setVehicle("car"));
        assertFalse(grsp.hasErrors());
        rsp = grsp.getBest();
        assertEquals(3, rsp.getPoints().getSize());
    }

    @Test
    public void testFailsForWrongConfig() throws IOException {
        instance = new GraphHopperOSM().init(
                new CmdArgs().
                        put("datareader.file", testOsm3).
                        put("datareader.dataaccess", "RAM").
                        put("graph.flag_encoders", "foot,car").
                        put(Parameters.CH.PREPARE + "weightings", "no")).
                setGraphHopperLocation(ghLoc);
        instance.importOrLoad();
        assertEquals(5, instance.getGraphHopperStorage().getNodes());
        instance.close();

        // different config (flagEncoder list)
        try {
            GraphHopper tmpGH = new GraphHopperOSM().init(
                    new CmdArgs().
                            put("datareader.file", testOsm3).
                            put("datareader.dataaccess", "RAM").
                            put("graph.flag_encoders", "foot").
                            put(Parameters.CH.PREPARE + "weightings", "no")).
                    setDataReaderFile(testOsm3);
            tmpGH.load(ghLoc);
            assertTrue(false);
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Encoding does not match"));
        }

        // different bytesForFlags should fail to load
        instance = new GraphHopperOSM().init(
                new CmdArgs().
                        put("datareader.file", testOsm3).
                        put("datareader.dataaccess", "RAM").
                        put("graph.flag_encoders", "foot,car").
                        put("graph.bytes_for_flags", 8).
                        put(Parameters.CH.PREPARE + "weightings", "no")).
                setDataReaderFile(testOsm3);
        try {
            instance.load(ghLoc);
            assertTrue(false);
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Configured graph.bytes_for_flags (8) is not equal to loaded 4"));
        }

        // different order is no longer okay, see #350
        try {
            GraphHopper tmpGH = new GraphHopperOSM().init(new CmdArgs().
                    put("datareader.file", testOsm3).
                    put("datareader.dataaccess", "RAM").
                    put(Parameters.CH.PREPARE + "weightings", "no").
                    put("graph.flag_encoders", "car,foot")).
                    setDataReaderFile(testOsm3);
            tmpGH.load(ghLoc);
            assertTrue(false);
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Encoding does not match"));
        }
    }

    @Test
    public void testNoNPE_ifLoadNotSuccessful() {
        instance = new GraphHopperOSM().
                setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("car"));
        try {
            // loading from empty directory
            new File(ghLoc).mkdirs();
            assertFalse(instance.load(ghLoc));
            instance.route(new GHRequest(10, 40, 12, 32));
            assertTrue(false);
        } catch (IllegalStateException ex) {
            assertEquals("Do a successful call to load or importOrLoad before routing", ex.getMessage());
        }
    }

    @Test
    public void testDoesNotCreateEmptyFolderIfLoadingFromNonExistingPath() {
        instance = new GraphHopperOSM().
                setEncodingManager(new EncodingManager("car"));

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
            assertTrue(false);
        } catch (IllegalStateException ex) {
            assertEquals("Load graph before importing OSM data", ex.getMessage());
        }

        // missing graph location
        instance = new GraphHopperOSM();
        try {
            instance.importOrLoad();
            assertTrue(false);
        } catch (IllegalStateException ex) {
            assertEquals("GraphHopperLocation is not specified. Call setGraphHopperLocation or init before", ex.getMessage());
        }

        // missing OSM file to import
        instance = new GraphHopperOSM().
                setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("car")).
                setGraphHopperLocation(ghLoc);
        try {
            instance.importOrLoad();
            assertTrue(false);
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
            assertTrue(false);
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Cannot load properties to fetch EncodingManager"));
        }

        // Import is possible even if no storeOnFlush is specified BUT here we miss the OSM file
        instance = new GraphHopperOSM().
                setStoreOnFlush(false).
                setEncodingManager(new EncodingManager("car")).
                setGraphHopperLocation(ghLoc);
        try {
            instance.importOrLoad();
            assertTrue(false);
        } catch (Exception ex) {
            assertEquals("Couldn't load from existing folder: " + ghLoc
                    + " but also cannot use file for DataReader as it wasn't specified!", ex.getMessage());
        }
    }

    @Test
    public void testFootOnly() {
        // now only footable ways are imported => no A D C and B D E => the other both ways have pillar nodes!
        instance = new GraphHopperOSM().setStoreOnFlush(false).
                setEncodingManager(new EncodingManager("foot")).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm3);
        instance.importOrLoad();

        assertEquals(2, instance.getGraphHopperStorage().getNodes());
        assertEquals(2, instance.getGraphHopperStorage().getAllEdges().getMaxId());

        // A to E only for foot
        GHResponse grsp = instance.route(new GHRequest(11.1, 50, 11.19, 52).setVehicle("foot"));
        assertFalse(grsp.hasErrors());
        PathWrapper rsp = grsp.getBest();
        // the last points snaps to the edge
        assertEquals(Helper.createPointList(11.1, 50, 10, 51, 11.194015, 51.995013), rsp.getPoints());
    }

    @Test
    public void testVia() {
        instance = new GraphHopperOSM().setStoreOnFlush(true).
                init(new CmdArgs().
                        put("datareader.file", testOsm3).
                        put("prepare.min_network_size", "1").
                        put("graph.flag_encoders", "car")).
                setGraphHopperLocation(ghLoc);
        instance.importOrLoad();

        // A -> B -> C
        GHPoint first = new GHPoint(11.1, 50);
        GHPoint second = new GHPoint(12, 51);
        GHPoint third = new GHPoint(11.2, 51.9);
        GHResponse rsp12 = instance.route(new GHRequest().addPoint(first).addPoint(second));
        assertFalse("should find 1->2", rsp12.hasErrors());
        assertEquals(147930.5, rsp12.getBest().getDistance(), .1);
        GHResponse rsp23 = instance.route(new GHRequest().addPoint(second).addPoint(third));
        assertFalse("should find 2->3", rsp23.hasErrors());
        assertEquals(176608.9, rsp23.getBest().getDistance(), .1);

        GHResponse grsp = instance.route(new GHRequest().addPoint(first).addPoint(second).addPoint(third));
        assertFalse("should find 1->2->3", grsp.hasErrors());
        PathWrapper rsp = grsp.getBest();
        assertEquals(rsp12.getBest().getDistance() + rsp23.getBest().getDistance(), rsp.getDistance(), 1e-6);
        assertEquals(5, rsp.getPoints().getSize());
        assertEquals(5, rsp.getInstructions().size());
        assertEquals(Instruction.REACHED_VIA, rsp.getInstructions().get(1).getSign());
    }

    @Test
    public void testGetPathsDirectionEnforcement1() {
        // Test enforce start direction
        // Note: This Test does not pass for CH enabled    
        instance = createSquareGraphInstance(false);

        // Start in middle of edge 4-5 
        GHPoint start = new GHPoint(0.0015, 0.002);
        // End at middle of edge 2-3
        GHPoint end = new GHPoint(0.002, 0.0005);

        GHRequest req = new GHRequest().addPoint(start, 180.).addPoint(end);
        GHResponse response = new GHResponse();
        List<Path> paths = instance.calcPaths(req, response);
        assertFalse(response.hasErrors());
        assertArrayEquals(new int[]{9, 5, 8, 3, 10}, paths.get(0).calcNodes().toArray());
    }

    @Test
    public void testGetPathsDirectionEnforcement2() {
        // Test enforce south start direction and east end direction
        instance = createSquareGraphInstance(false);

        // Start in middle of edge 4-5 
        GHPoint start = new GHPoint(0.0015, 0.002);
        // End at middle of edge 2-3
        GHPoint end = new GHPoint(0.002, 0.0005);

        GHRequest req = new GHRequest().addPoint(start, 180.).addPoint(end, 90.);
        GHResponse response = new GHResponse();
        List<Path> paths = instance.calcPaths(req, response);
        assertFalse(response.hasErrors());
        assertArrayEquals(new int[]{9, 5, 8, 1, 2, 10}, paths.get(0).calcNodes().toArray());

        // Test uni-directional case
        req.setAlgorithm(DIJKSTRA);
        response = new GHResponse();
        paths = instance.calcPaths(req, response);
        assertFalse(response.hasErrors());
        assertArrayEquals(new int[]{9, 5, 8, 1, 2, 10}, paths.get(0).calcNodes().toArray());
    }

    @Test
    public void testGetPathsDirectionEnforcement3() {
        instance = createSquareGraphInstance(false);

        // Start in middle of edge 4-5 
        GHPoint start = new GHPoint(0.0015, 0.002);
        // End at middle of edge 2-3
        GHPoint end = new GHPoint(0.002, 0.0005);
        // Via Point betweeen 8-7
        GHPoint via = new GHPoint(0.0005, 0.001);

        GHRequest req = new GHRequest().addPoint(start).addPoint(via, 0.).addPoint(end);
        GHResponse response = new GHResponse();
        List<Path> paths = instance.calcPaths(req, response);
        assertFalse(response.hasErrors());
        assertEquals(IntArrayList.from(9, 5, 6, 7, 11), paths.get(0).calcNodes());
    }

    @Test
    public void testGetPathsDirectionEnforcement4() {
        // Test straight via routing
        instance = createSquareGraphInstance(false);

        // Start in middle of edge 4-5 
        GHPoint start = new GHPoint(0.0015, 0.002);
        // End at middle of edge 2-3
        GHPoint end = new GHPoint(0.002, 0.0005);
        // Via Point betweeen 8-3
        GHPoint via = new GHPoint(0.0015, 0.001);
        GHRequest req = new GHRequest().addPoint(start).addPoint(via).addPoint(end);
        req.getHints().put(Routing.PASS_THROUGH, true);
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
        instance = createSquareGraphInstance(false);

        // Start in middle of edge 4-5 
        GHPoint start = new GHPoint(0.0015, 0.002);
        // End at middle of edge 2-3
        GHPoint end = new GHPoint(0.002, 0.0005);
        // First go south and than come from west to via-point at 7-6. Then go back over previously punished (11)-4 edge
        GHPoint via = new GHPoint(0.000, 0.0015);
        GHRequest req = new GHRequest().addPoint(start, 0.).addPoint(via, 3.14 / 2).addPoint(end);
        req.getHints().put(Routing.PASS_THROUGH, true);
        GHResponse response = new GHResponse();
        List<Path> paths = instance.calcPaths(req, response);
        assertFalse(response.hasErrors());
        assertEquals(IntArrayList.from(9, 4, 3, 8, 7, 11), paths.get(0).calcNodes());
        assertEquals(IntArrayList.from(11, 6, 5, 9, 4, 3, 10), paths.get(1).calcNodes());
    }

    @Test
    public void testGetPathsDirectionEnforcement6() {
        // Test if query results at tower nodes are ignored
        instance = createSquareGraphInstance(false);

        // QueryPoints directly on TowerNodes 
        GHPoint start = new GHPoint(0, 0);
        GHPoint via = new GHPoint(0.002, 0.000);
        GHPoint end = new GHPoint(0.002, 0.002);

        GHRequest req = new GHRequest().addPoint(start, 90.).addPoint(via, 270.).addPoint(end, 270.);
        GHResponse response = new GHResponse();
        List<Path> paths = instance.calcPaths(req, response);
        assertFalse(response.hasErrors());
        assertArrayEquals(new int[]{0, 1, 2}, paths.get(0).calcNodes().toArray());
        assertArrayEquals(new int[]{2, 3, 4}, paths.get(1).calcNodes().toArray());
    }

    private GraphHopper createSquareGraphInstance(boolean withCH) {
        CarFlagEncoder carEncoder = new CarFlagEncoder();
        EncodingManager encodingManager = new EncodingManager(carEncoder);
        Weighting weighting = new FastestWeighting(carEncoder);
        GraphHopperStorage g = new GraphHopperStorage(Collections.singletonList(weighting), new RAMDirectory(), encodingManager,
                false, new GraphExtension.NoOpExtension()).
                create(20);

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
                setCHEnabled(withCH).
                setEncodingManager(encodingManager);
        tmp.getCHFactoryDecorator().setWeightingsAsStrings("fastest");
        tmp.setGraphHopperStorage(g);
        tmp.postProcessing();

        return tmp;
    }

    @Test
    public void testCustomFactoryForNoneCH() {
        CarFlagEncoder carEncoder = new CarFlagEncoder();
        EncodingManager em = new EncodingManager(carEncoder);
        // Weighting weighting = new FastestWeighting(carEncoder);
        instance = new GraphHopperOSM().setStoreOnFlush(false).setCHEnabled(false).
                setEncodingManager(em).
                setGraphHopperLocation(ghLoc).
                setDataReaderFile(testOsm);
        final RoutingAlgorithmFactory af = new RoutingAlgorithmFactorySimple();
        instance.addAlgorithmFactoryDecorator(new RoutingAlgorithmFactoryDecorator() {
            @Override
            public void init(CmdArgs args) {
            }

            @Override
            public RoutingAlgorithmFactory getDecoratedAlgorithmFactory(RoutingAlgorithmFactory algoFactory, HintsMap map) {
                return af;
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        });
        instance.importOrLoad();

        assertTrue(af == instance.getAlgorithmFactory(null));

        // test that hints are passed to algorithm opts
        final AtomicInteger cnt = new AtomicInteger(0);
        instance.addAlgorithmFactoryDecorator(new RoutingAlgorithmFactoryDecorator() {
            @Override
            public void init(CmdArgs args) {
            }

            public RoutingAlgorithmFactory getDecoratedAlgorithmFactory(RoutingAlgorithmFactory algoFactory, HintsMap map) {
                return new RoutingAlgorithmFactorySimple() {
                    @Override
                    public RoutingAlgorithm createAlgo(Graph g, AlgorithmOptions opts) {
                        cnt.addAndGet(1);
                        assertFalse(opts.getHints().getBool("test", true));
                        return super.createAlgo(g, opts);
                    }
                };
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        });
        GHRequest req = new GHRequest(51.2492152, 9.4317166, 51.2, 9.4);
        req.getHints().put("test", false);
        instance.route(req);
        assertEquals(1, cnt.get());
    }

    @Test
    public void testMultipleCHPreparationsInParallel() {
        HashMap<String, Integer> shortcutCountMap = new HashMap<String, Integer>();
        // try all parallelization modes        
        for (int threadCount = 1; threadCount < 6; threadCount++) {
            EncodingManager em = new EncodingManager(Arrays.asList(new CarFlagEncoder(), new MotorcycleFlagEncoder(),
                    new MountainBikeFlagEncoder(), new RacingBikeFlagEncoder(), new FootFlagEncoder()), 8);

            GraphHopper tmpGH = new GraphHopperOSM().
                    setStoreOnFlush(false).
                    setEncodingManager(em).
                    setGraphHopperLocation(ghLoc).
                    setDataReaderFile(testOsm);
            tmpGH.getCHFactoryDecorator().setPreparationThreads(threadCount);

            tmpGH.importOrLoad();

            assertEquals(5, tmpGH.getCHFactoryDecorator().getPreparations().size());
            for (RoutingAlgorithmFactory raf : tmpGH.getCHFactoryDecorator().getPreparations()) {
                PrepareContractionHierarchies pch = (PrepareContractionHierarchies) raf;
                assertTrue("Preparation wasn't run! [" + threadCount + "]", pch.isPrepared());

                String name = AbstractWeighting.weightingToFileName(pch.getWeighting());
                Integer singleThreadShortcutCount = shortcutCountMap.get(name);
                if (singleThreadShortcutCount == null)
                    shortcutCountMap.put(name, pch.getShortcuts());
                else
                    assertEquals((int) singleThreadShortcutCount, pch.getShortcuts());

                String keyError = Parameters.CH.PREPARE + "error." + name;
                String valueError = tmpGH.getGraphHopperStorage().getProperties().get(keyError);
                assertTrue("Properties for " + name + " should NOT contain error " + valueError + " [" + threadCount + "]", valueError.isEmpty());

                String key = Parameters.CH.PREPARE + "date." + name;
                String value = tmpGH.getGraphHopperStorage().getProperties().get(key);
                assertTrue("Properties for " + name + " did NOT contain finish date [" + threadCount + "]", !value.isEmpty());
            }
            tmpGH.close();
        }
    }

    @Test
    public void testMultipleLMPreparationsInParallel() {
        HashMap<String, Integer> landmarkCount = new HashMap<String, Integer>();
        // try all parallelization modes
        for (int threadCount = 1; threadCount < 6; threadCount++) {
            EncodingManager em = new EncodingManager(Arrays.asList(new CarFlagEncoder(), new MotorcycleFlagEncoder(),
                    new MountainBikeFlagEncoder(), new RacingBikeFlagEncoder(), new FootFlagEncoder()), 8);

            GraphHopper tmpGH = new GraphHopperOSM().
                    setStoreOnFlush(false).
                    setCHEnabled(false).
                    setEncodingManager(em).
                    setGraphHopperLocation(ghLoc).
                    setDataReaderFile(testOsm);
            tmpGH.getLMFactoryDecorator().
                    addWeighting("fastest").
                    setEnabled(true).
                    setPreparationThreads(threadCount);

            tmpGH.importOrLoad();

            assertEquals(5, tmpGH.getLMFactoryDecorator().getPreparations().size());
            for (PrepareLandmarks prepLM : tmpGH.getLMFactoryDecorator().getPreparations()) {
                assertTrue("Preparation wasn't run! [" + threadCount + "]", prepLM.isPrepared());

                String name = AbstractWeighting.weightingToFileName(prepLM.getWeighting());
                Integer singleThreadShortcutCount = landmarkCount.get(name);
                if (singleThreadShortcutCount == null)
                    landmarkCount.put(name, prepLM.getSubnetworksWithLandmarks());
                else
                    assertEquals((int) singleThreadShortcutCount, prepLM.getSubnetworksWithLandmarks());

                String keyError = Parameters.Landmark.PREPARE + "error." + name;
                String valueError = tmpGH.getGraphHopperStorage().getProperties().get(keyError);
                assertTrue("Properties for " + name + " should NOT contain error " + valueError + " [" + threadCount + "]", valueError.isEmpty());

                String key = Parameters.Landmark.PREPARE + "date." + name;
                String value = tmpGH.getGraphHopperStorage().getProperties().get(key);
                assertTrue("Properties for " + name + " did NOT contain finish date [" + threadCount + "]", !value.isEmpty());
            }
            tmpGH.close();
        }
    }

    @Test
    public void testGetWeightingForCH() {
        TestEncoder truck = new TestEncoder("truck");
        TestEncoder simpleTruck = new TestEncoder("simple_truck");

        // use simple truck first
        EncodingManager em = new EncodingManager(simpleTruck, truck);
        CHAlgoFactoryDecorator decorator = new CHAlgoFactoryDecorator();
        Weighting fwSimpleTruck = new FastestWeighting(simpleTruck);
        Weighting fwTruck = new FastestWeighting(truck);
        RAMDirectory ramDir = new RAMDirectory();
        GraphHopperStorage storage = new GraphHopperStorage(Arrays.asList(fwSimpleTruck, fwTruck), ramDir, em, false, new GraphExtension.NoOpExtension());
        decorator.addWeighting(fwSimpleTruck);
        decorator.addWeighting(fwTruck);
        decorator.addPreparation(new PrepareContractionHierarchies(ramDir, storage, storage.getGraph(CHGraph.class, fwSimpleTruck), fwSimpleTruck, TraversalMode.NODE_BASED));
        decorator.addPreparation(new PrepareContractionHierarchies(ramDir, storage, storage.getGraph(CHGraph.class, fwTruck), fwTruck, TraversalMode.NODE_BASED));

        HintsMap wMap = new HintsMap("fastest");
        wMap.put("vehicle", "truck");
        assertEquals("fastest|truck", ((PrepareContractionHierarchies) decorator.getDecoratedAlgorithmFactory(null, wMap)).getWeighting().toString());
        wMap.put("vehicle", "simple_truck");
        assertEquals("fastest|simple_truck", ((PrepareContractionHierarchies) decorator.getDecoratedAlgorithmFactory(null, wMap)).getWeighting().toString());

        // make sure weighting cannot be mixed
        decorator.addWeighting(fwTruck);
        decorator.addWeighting(fwSimpleTruck);
        try {
            decorator.addPreparation(new PrepareContractionHierarchies(ramDir, storage, storage.getGraph(CHGraph.class, fwSimpleTruck), fwSimpleTruck, TraversalMode.NODE_BASED));
            assertTrue(false);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testGetMultipleWeightingsForCH() {
        EncodingManager em = new EncodingManager(Arrays.asList(new CarFlagEncoder()), 8);

        GraphHopper tmpGH = new GraphHopperOSM().
                setStoreOnFlush(false).
                setEncodingManager(em);
        tmpGH.getCHFactoryDecorator().setWeightingsAsStrings("fastest", "shortest");

        assertEquals(2, tmpGH.getCHFactoryDecorator().getWeightingsAsStrings().size());
    }

    class TestEncoder extends CarFlagEncoder {
        private final String name;

        public TestEncoder(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
