/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
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

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import java.io.File;
import java.io.IOException;
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
        instance.close();
        Helper.removeDir(new File(ghLoc));
    }

    @Test
    public void testLoadOSM() throws IOException {
        instance = new GraphHopper().setInMemory(true, true).setEncodingManager(new EncodingManager("CAR")).
                setGraphHopperLocation(ghLoc).setOSMFile(testOsm).
                setFinishPath(false);
        instance.importOrLoad();
        GHResponse ph = instance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4));
        assertTrue(ph.isFound());
        assertEquals(3, ph.getPoints().getSize());

        instance.close();
        instance = new GraphHopper().setInMemory(true, true);
        assertTrue(instance.load(ghLoc));
        ph = instance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4));
        assertTrue(ph.isFound());
        assertEquals(3, ph.getPoints().getSize());
    }

    @Test
    public void testPrepare() throws IOException
    {
        instance = new GraphHopper().setInMemory(true, false).setEncodingManager(new EncodingManager("CAR")).
                setCHShortcuts("shortest").
                setGraphHopperLocation(ghLoc).setOSMFile(testOsm);
        instance.importOrLoad();
        GHResponse ph = instance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).setAlgorithm("dijkstrabi"));
        assertTrue(ph.isFound());
        assertEquals(3, ph.getPoints().getSize());
    }

    @Test
    public void testFootAndCar() throws IOException
    {
        // now all ways are imported
        instance = new GraphHopper().setInMemory(true, false).setEncodingManager(new EncodingManager("CAR,FOOT")).
                disableCHShortcuts().
                setGraphHopperLocation(ghLoc).setOSMFile(testOsm3).
                setFinishPath(false);
        instance.importOrLoad();

        assertEquals(5, instance.getGraph().getNodes());
        assertEquals(8, instance.getGraph().getAllEdges().getMaxId());

        // A to D
        GHResponse res = instance.route(new GHRequest(11.1, 50, 11.3, 51).setVehicle(EncodingManager.CAR));
        assertTrue(res.isFound());
        assertEquals(2, res.getPoints().getSize());
        // => found D
        assertEquals(51, res.getPoints().getLongitude(1), 1e-3);
        assertEquals(11.3, res.getPoints().getLatitude(1), 1e-3);

        // A to D not allowed for foot. But the location index will choose a node close to D accessible to FOOT        
        res = instance.route(new GHRequest(11.1, 50, 11.3, 51).setVehicle(EncodingManager.FOOT));
        assertTrue(res.isFound());
        assertEquals(2, res.getPoints().getSize());
        // => found B
        assertEquals(51, res.getPoints().getLongitude(1), 1e-3);
        assertEquals(12, res.getPoints().getLatitude(1), 1e-3);

        // A to E only for foot
        res = instance.route(new GHRequest(11.1, 50, 10, 51).setVehicle(EncodingManager.FOOT));
        assertTrue(res.isFound());
        assertEquals(2, res.getPoints().getSize());

        // A D E for car
        res = instance.route(new GHRequest(11.1, 50, 10, 51).setVehicle(EncodingManager.CAR));
        assertTrue(res.isFound());
        assertEquals(2, res.getPoints().getSize()); // 2 not 3 because of AD is one way => starting on AD mean starting at D.
    }

    @Test
    public void testFailsForWrongConfig() throws IOException
    {
        instance = new GraphHopper().init(
                new CmdArgs().
                put("osmreader.acceptWay", "FOOT,CAR").
                put("prepare.chShortcuts", "no").
                put("osmreader.osm", testOsm3)).setGraphHopperLocation(ghLoc).
                setFinishPath(false);
        instance.importOrLoad();
        assertEquals(5, instance.getGraph().getNodes());
        instance.close();

        instance = new GraphHopper().init(
                new CmdArgs().
                put("osmreader.acceptWay", "FOOT").
                put("prepare.chShortcuts", "no").
                put("osmreader.osm", testOsm3)).setOSMFile(testOsm3);
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
                put("osmreader.acceptWay", "CAR,FOOT").
                put("prepare.chShortcuts", "no").
                put("osmreader.osm", testOsm3)).setOSMFile(testOsm3);
        assertTrue(instance.load(ghLoc));
        assertEquals(5, instance.getGraph().getNodes());
    }

    @Test
    public void testFailsForMissingParameters() throws IOException
    {
        // missing load of graph
        instance = new GraphHopper();
        try
        {
            instance.importOSM(testOsm);
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

        // missing encoding manager
        instance = new GraphHopper().setInMemory(true, true).setGraphHopperLocation(ghLoc);
        try
        {
            instance.importOrLoad();
            assertTrue(false);
        } catch (IllegalStateException ex)
        {
            assertEquals("No encodingManager was specified", ex.getMessage());
        }
    }

    @Test
    public void testFootOnly() throws IOException
    {
        // now only footable ways are imported => no A D C and B D E => the other both ways have pillar nodes!
        instance = new GraphHopper().setInMemory(true, false).setEncodingManager(new EncodingManager("FOOT")).
                setGraphHopperLocation(ghLoc).setOSMFile(testOsm3);
        instance.importOrLoad();

        assertEquals(2, instance.getGraph().getNodes());
        assertEquals(2, instance.getGraph().getAllEdges().getMaxId());

        // A to E only for foot
        GHResponse res = instance.route(new GHRequest(11.1, 50, 11.2, 52).setVehicle(EncodingManager.FOOT));
        assertTrue(res.isFound());
        assertEquals(3, res.getPoints().getSize());
    }

    @Test
    public void testPrepareOnly() throws IOException
    {
        instance = new GraphHopper().setInMemory(true, true).setCHShortcuts("shortest").
                setEncodingManager(new EncodingManager("FOOT")).
                doPrepare(false).
                setGraphHopperLocation(ghLoc).setOSMFile(testOsm3);
        instance.importOrLoad();
        instance.close();

        instance = new GraphHopper().setInMemory(true, true).setCHShortcuts("shortest").
                setGraphHopperLocation(ghLoc).setOSMFile(testOsm3);

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
        instance = new GraphHopper().setInMemory(true, true).setCHShortcuts("shortest").
                setGraphHopperLocation(ghLoc).setOSMFile(testOsm3);
        instance.load(ghLoc);
    }
}
