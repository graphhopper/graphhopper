/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License, 
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

import com.graphhopper.reader.DataReader;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.shapes.GHPoint;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author Peter Karich
 */
public class GraphHopperTest
{
    private static final String ghLoc = "./target/tmp/ghosm";
    private static final String testOsm = "./src/test/resources/com/graphhopper/reader/test-osm.xml";
    private static final String testOsm3 = "./src/test/resources/com/graphhopper/reader/test-osm3.xml";
    private GraphHopper instance;

    @Before
    public void setUp()
    {
        Helper.removeDir(new File(ghLoc));
    }

    @After
    public void tearDown()
    {
        if (instance != null)
            instance.close();
        Helper.removeDir(new File(ghLoc));
    }

    @Test
    public void testLoadOSM()
    {
        GraphHopper closableInstance = new GraphHopper().setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("CAR")).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        closableInstance.importOrLoad();
        GHResponse ph = closableInstance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4));
        assertTrue(ph.isFound());
        assertEquals(3, ph.getPoints().getSize());

        closableInstance.close();
        closableInstance = new GraphHopper().setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("CAR"));
        assertTrue(closableInstance.load(ghLoc));
        ph = closableInstance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4));
        assertTrue(ph.isFound());
        assertEquals(3, ph.getPoints().getSize());

        closableInstance.close();
        try
        {
            ph = closableInstance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4));
            assertTrue(false);
        } catch (Exception ex)
        {
            assertEquals("You need to create a new GraphHopper instance as it is already closed", ex.getMessage());
        }

        try
        {
            QueryResult qr = closableInstance.getLocationIndex().findClosest(51.2492152, 9.4317166, EdgeFilter.ALL_EDGES);
            assertTrue(false);
        } catch (Exception ex)
        {
            assertEquals("You need to create a new LocationIndex instance as it is already closed", ex.getMessage());
        }
    }

    @Test
    public void testLoadOSMNoCH()
    {
        GraphHopper gh = new GraphHopper().setStoreOnFlush(true).
                setCHEnable(false).
                setEncodingManager(new EncodingManager("CAR")).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        gh.importOrLoad();
        GHResponse ph = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4));
        assertTrue(ph.isFound());
        assertEquals(3, ph.getPoints().getSize());

        gh.close();
        gh = new GraphHopper().setStoreOnFlush(true).
                setCHEnable(false).
                setEncodingManager(new EncodingManager("CAR"));
        assertTrue(gh.load(ghLoc));
        ph = gh.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4));
        assertTrue(ph.isFound());
        assertEquals(3, ph.getPoints().getSize());

        gh.close();
    }

    @Test
    public void testAllowMultipleReadingInstances()
    {
        GraphHopper instance1 = new GraphHopper().setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("CAR")).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        instance1.importOrLoad();

        GraphHopper instance2 = new GraphHopper().setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("CAR")).
                setOSMFile(testOsm);
        instance2.load(ghLoc);

        GraphHopper instance3 = new GraphHopper().setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("CAR")).
                setOSMFile(testOsm);
        instance3.load(ghLoc);

        instance1.close();
        instance2.close();
        instance3.close();
    }

    @Test
    public void testDoNotAllowWritingAndLoadingAtTheSameTime() throws Exception
    {
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        final GraphHopper instance1 = new GraphHopper()
        {
            @Override
            protected DataReader importData() throws IOException
            {
                try
                {
                    latch2.countDown();
                    latch1.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException ex)
                {
                }
                return super.importData();
            }
        }.setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("CAR")).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        final AtomicReference<Exception> ar = new AtomicReference<Exception>();
        Thread thread = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    instance1.importOrLoad();
                } catch (Exception ex)
                {
                    ar.set(ex);
                }
            }
        };
        thread.start();

        GraphHopper instance2 = new GraphHopper().setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("CAR")).
                setOSMFile(testOsm);
        try
        {
            // let thread reach the CountDownLatch
            latch2.await(3, TimeUnit.SECONDS);
            // now importOrLoad should have create a lock which this load call does not like
            instance2.load(ghLoc);
            assertTrue(false);
        } catch (RuntimeException ex)
        {
            assertNotNull(ex);
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("To avoid reading partial data"));
        } finally
        {
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
    public void testPrepare()
    {
        instance = new GraphHopper().
                setStoreOnFlush(false).
                setEncodingManager(new EncodingManager("CAR")).
                setCHWeighting("shortest").
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        instance.importOrLoad();
        GHResponse ph = instance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).setAlgorithm("dijkstrabi"));
        assertTrue(ph.isFound());
        assertEquals("(51.24921503475044,9.431716451757769), (52.0,9.0), (51.199999850988384,9.39999970197677)", ph.getPoints().toString());
        assertEquals(3, ph.getPoints().getSize());
    }

    @Test
    public void testSortedGraph_noCH()
    {
        instance = new GraphHopper().setStoreOnFlush(false).
                setSortGraph(true).
                setEncodingManager(new EncodingManager("CAR")).
                setCHEnable(false).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm);
        instance.importOrLoad();
        GHResponse ph = instance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).setAlgorithm("dijkstrabi"));
        assertTrue(ph.isFound());
        assertEquals(3, ph.getPoints().getSize());
        assertEquals(new GHPoint(51.24921503475044, 9.431716451757769), ph.getPoints().toGHPoint(0));
        assertEquals(new GHPoint(52.0, 9.0), ph.getPoints().toGHPoint(1));
        assertEquals(new GHPoint(51.199999850988384, 9.39999970197677), ph.getPoints().toGHPoint(2));
    }

    @Test
    public void testFootAndCar()
    {
        // now all ways are imported
        instance = new GraphHopper().setStoreOnFlush(false).
                setEncodingManager(new EncodingManager("CAR,FOOT")).
                setCHEnable(false).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm3);
        instance.importOrLoad();

        assertEquals(5, instance.getGraph().getNodes());
        assertEquals(8, instance.getGraph().getAllEdges().getMaxId());

        // A to D
        GHResponse res = instance.route(new GHRequest(11.1, 50, 11.3, 51).setVehicle(EncodingManager.CAR));
        assertFalse(res.hasErrors());
        assertTrue(res.isFound());
        assertEquals(3, res.getPoints().getSize());
        // => found A and D
        assertEquals(50, res.getPoints().getLongitude(0), 1e-3);
        assertEquals(11.1, res.getPoints().getLatitude(0), 1e-3);
        assertEquals(51, res.getPoints().getLongitude(2), 1e-3);
        assertEquals(11.3, res.getPoints().getLatitude(2), 1e-3);

        // A to D not allowed for foot. But the location index will choose a node close to D accessible to FOOT        
        res = instance.route(new GHRequest(11.1, 50, 11.3, 51).setVehicle(EncodingManager.FOOT));
        assertTrue(res.isFound());
        assertEquals(2, res.getPoints().getSize());
        // => found a point on edge A-B        
        assertEquals(11.680, res.getPoints().getLatitude(1), 1e-3);
        assertEquals(50.644, res.getPoints().getLongitude(1), 1e-3);

        // A to E only for foot
        res = instance.route(new GHRequest(11.1, 50, 10, 51).setVehicle(EncodingManager.FOOT));
        assertTrue(res.isFound());
        assertEquals(3, res.getPoints().size());

        // A D E for car
        res = instance.route(new GHRequest(11.1, 50, 10, 51).setVehicle(EncodingManager.CAR));
        assertTrue(res.isFound());
        assertEquals(4, res.getPoints().getSize());
    }

    @Test
    public void testFailsForWrongConfig() throws IOException
    {
        instance = new GraphHopper().init(
                new CmdArgs().
                put("osmreader.osm", testOsm3).
                put("osmreader.dataaccess", "RAM").
                put("osmreader.acceptWay", "FOOT,CAR").
                put("prepare.chWeighting", "no")).
                setGraphHopperLocation(ghLoc);
        instance.importOrLoad();
        assertEquals(5, instance.getGraph().getNodes());
        instance.close();

        instance = new GraphHopper().init(
                new CmdArgs().
                put("osmreader.osm", testOsm3).
                put("osmreader.dataaccess", "RAM").
                put("osmreader.acceptWay", "FOOT").
                put("prepare.chWeighting", "no")).
                setOSMFile(testOsm3);
        try
        {
            instance.load(ghLoc);
            assertTrue(false);
        } catch (Exception ex)
        {
        }

        // different order should be ok
        instance = new GraphHopper().init(
                new CmdArgs().
                put("osmreader.osm", testOsm3).
                put("osmreader.dataaccess", "RAM").
                put("prepare.chWeighting", "no").
                put("osmreader.acceptWay", "CAR,FOOT")).
                setOSMFile(testOsm3);
        assertTrue(instance.load(ghLoc));
        assertEquals(5, instance.getGraph().getNodes());
    }

    @Test
    public void testNoNPE_ifLoadNotSuccessful()
    {
        // missing import of graph
        instance = new GraphHopper().
                setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("CAR"));
        try
        {
            assertFalse(instance.load(ghLoc));
            instance.route(new GHRequest(10, 40, 12, 32));
            assertTrue(false);
        } catch (IllegalStateException ex)
        {
            assertEquals("Call load or importOrLoad before routing", ex.getMessage());
        }
    }

    @Test
    public void testFailsForMissingParameters() throws IOException
    {
        // missing load of graph
        instance = new GraphHopper();
        try
        {
            instance.setOSMFile(testOsm).importData();
            assertTrue(false);
        } catch (IllegalStateException ex)
        {
            assertEquals("Load graph before importing OSM data", ex.getMessage());
        }

        // missing graph location
        instance = new GraphHopper();
        try
        {
            instance.importOrLoad();
            assertTrue(false);
        } catch (IllegalStateException ex)
        {
            assertEquals("graphHopperLocation is not specified. call init before", ex.getMessage());
        }

        // missing OSM file to import
        instance = new GraphHopper().
                setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("CAR")).
                setGraphHopperLocation(ghLoc);
        try
        {
            instance.importOrLoad();
            assertTrue(false);
        } catch (IllegalStateException ex)
        {
            assertEquals("Couldn't load from existing folder: " + ghLoc
                    + " but also cannot import from OSM file as it wasn't specified!", ex.getMessage());
        }

        // missing encoding manager          
        instance = new GraphHopper().
                setStoreOnFlush(true).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm3);
        try
        {
            instance.importOrLoad();
            assertTrue(false);
        } catch (IllegalStateException ex)
        {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Cannot load properties to fetch EncodingManager"));
        }

        // Import is possible even if no storeOnFlush is specified BUT here we miss the OSM file
        instance = new GraphHopper().
                setStoreOnFlush(false).
                setEncodingManager(new EncodingManager("CAR")).
                setGraphHopperLocation(ghLoc);
        try
        {
            instance.importOrLoad();
            assertTrue(false);
        } catch (Exception ex)
        {
            assertEquals("Couldn't load from existing folder: " + ghLoc
                    + " but also cannot import from OSM file as it wasn't specified!", ex.getMessage());
        }
    }

    @Test
    public void testFootOnly()
    {
        // now only footable ways are imported => no A D C and B D E => the other both ways have pillar nodes!
        instance = new GraphHopper().setStoreOnFlush(false).
                setEncodingManager(new EncodingManager("FOOT")).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm3);
        instance.importOrLoad();

        assertEquals(2, instance.getGraph().getNodes());
        assertEquals(2, instance.getGraph().getAllEdges().getMaxId());

        // A to E only for foot
        GHResponse res = instance.route(new GHRequest(11.1, 50, 11.2, 52).setVehicle(EncodingManager.FOOT));
        assertTrue(res.isFound());
        assertEquals(3, res.getPoints().getSize());
    }

    @Test
    public void testPrepareOnly()
    {
        instance = new GraphHopper().setStoreOnFlush(true).
                setCHWeighting("shortest").
                setEncodingManager(new EncodingManager("FOOT")).
                setDoPrepare(false).
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm3);
        instance.importOrLoad();
        instance.close();

        instance = new GraphHopper().setStoreOnFlush(true).
                setCHWeighting("shortest").
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm3);

        // wrong encoding manager
        instance.setEncodingManager(new EncodingManager("CAR"));
        try
        {
            instance.load(ghLoc);
            assertTrue(false);
        } catch (IllegalStateException ex)
        {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Encoding does not match:"));
        }

        // use the encoding manager from the graph
        instance = new GraphHopper().setStoreOnFlush(true).
                setEncodingManager(new EncodingManager("FOOT")).
                setCHWeighting("shortest").
                setGraphHopperLocation(ghLoc).
                setOSMFile(testOsm3);
        instance.load(ghLoc);
    }

    @Test
    public void testVia()
    {
        instance = new GraphHopper().setStoreOnFlush(true).
                init(new CmdArgs().
                        put("osmreader.osm", testOsm3).
                        put("prepare.minNetworkSize", "1").
                        put("graph.acceptWay", "CAR")).
                setGraphHopperLocation(ghLoc);
        instance.importOrLoad();

        // A -> B -> C
        GHPoint first = new GHPoint(11.1, 50);
        GHPoint second = new GHPoint(12, 51);
        GHPoint third = new GHPoint(11.2, 51.9);
        GHResponse rsp12 = instance.route(new GHRequest().addPoint(first).addPoint(second));
        assertTrue("should find 1->2", rsp12.isFound());
        assertEquals(147931.5, rsp12.getDistance(), .1);
        GHResponse rsp23 = instance.route(new GHRequest().addPoint(second).addPoint(third));
        assertTrue("should find 2->3", rsp23.isFound());
        assertEquals(176608.9, rsp23.getDistance(), .1);

        GHResponse rsp = instance.route(new GHRequest().addPoint(first).addPoint(second).addPoint(third));

        assertFalse(rsp.hasErrors());
        assertTrue("should find 1->2->3", rsp.isFound());
        assertEquals(rsp12.getDistance() + rsp23.getDistance(), rsp.getDistance(), 1e-6);
        assertEquals(5, rsp.getPoints().getSize());
        assertEquals(5, rsp.getInstructions().size());
        assertEquals(Instruction.REACHED_VIA, rsp.getInstructions().get(1).getSign());
    }
}
