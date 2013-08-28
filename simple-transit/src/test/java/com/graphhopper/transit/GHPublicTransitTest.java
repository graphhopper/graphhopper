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
package com.graphhopper.transit;

import com.graphhopper.GHResponse;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.transit.routing.util.PublicTransitFlagEncoder;
import com.graphhopper.util.Helper;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author tbuerli
 */
public class GHPublicTransitTest
{
    private static final String ghLoc = "./target/tmp/ghpt";
    private static final String testGTFS = "./src/test/resources/com/graphhopper/transit/reader/test-gtfs2.zip";

    public GHPublicTransitTest()
    {
    }

    @Before
    public void setUp()
    {
        Helper.removeDir(new File(ghLoc));
    }

    @After
    public void tearDown()
    {
        Helper.removeDir(new File(ghLoc));
    }

    /**
     * Test of getEncodingManager method, of class GHPublicTransit.
     */
    @Test
    public void testEncodingManager()
    {
        GHPublicTransit instance = new GHPublicTransit();
        EncodingManager result = instance.getEncodingManager();
        assertTrue(result.supports("PUBLIC"));
        assertTrue(result.getEncoder("PUBLIC") instanceof PublicTransitFlagEncoder);
    }

    @Test
    public void testLoadGTFS() throws IOException
    {
        GHPublicTransit instance = new GHPublicTransit().setInMemory(true, true).setGraphHopperLocation(ghLoc).setGtfsFile(testGTFS);
        instance.importOrLoad();
        int startTime = 22800; // 06:20
        TransitRequest request = new TransitRequest(36.905697, -116.76218, startTime, 36.914944, -116.761472);
        request.setVehicle("PUBLIC");
        request.setAlgorithm("dijkstra");
        GHResponse ph = instance.route(request);
        assertTrue(ph.isFound());
        assertEquals(11, ph.getPoints().getSize());
        instance.close();
    }

    @Test
    public void testPrepare() throws IOException
    {
        GHPublicTransit instance = new GHPublicTransit().setInMemory(true, false).setGraphHopperLocation(ghLoc).setGtfsFile(testGTFS);
        instance.importOrLoad();
        int startTime = 22800; // 06:20
        TransitRequest request = new TransitRequest(36.905697, -116.76218, startTime, 36.914944, -116.761472);
        request.setVehicle("PUBLIC");
        request.setAlgorithm("dijkstra");
        GHResponse ph = instance.route(request);
        assertTrue(ph.isFound());
        assertEquals(11, ph.getPoints().getSize());
        instance.close();
    }

    @Test
    public void TestRoute()
    {
        GHPublicTransit instance = new GHPublicTransit().setInMemory(true, true).setGraphHopperLocation(ghLoc).setGtfsFile(testGTFS);
        instance.importOrLoad();
        int startTime = 23400; // 06:30
        TransitRequest request = new TransitRequest(36.905697, -116.76218, startTime, 36.914944, -116.761472);
        request.setVehicle("PUBLIC");
        GHResponse ph = instance.route(request);
        assertTrue(ph.isFound());
        assertEquals(9, ph.getPoints().getSize());
        assertEquals(1140 + instance.getDefaultAlightTime(), ph.getDistance(), 1E-5);

        request = new TransitRequest(36.905697, -116.76218, startTime, 36.915682, -116.751677);
        request.setVehicle("PUBLIC");
        ph = instance.route(request);
        assertTrue(ph.isFound());
        assertEquals(11, ph.getPoints().getSize());
        assertEquals(1560 + instance.getDefaultAlightTime(), ph.getDistance(), 1E-5);
        instance.close();
    }
    
    @Test
    public void TestWrongVehicle()  {
        GHPublicTransit instance = new GHPublicTransit().setInMemory(true, true).setGraphHopperLocation(ghLoc).setGtfsFile(testGTFS);
        instance.importOrLoad();
        int startTime = 23400; // 06:30
        TransitRequest request = new TransitRequest(36.905697, -116.76218, startTime, 36.914944, -116.761472);
        GHResponse ph = instance.route(request);
        assertTrue(ph.hasErrors());
        request.setVehicle("CAR");
        ph = instance.route(request);
        assertTrue(ph.hasErrors());
        instance.close();
    }
}