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
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class GraphHopperTest {

    private static final String ghLoc = "./target/tmp/ghosm";
    private static final String testOsm = "./src/test/resources/com/graphhopper/reader/test-osm.xml";
    private static final String testOsm3 = "./src/test/resources/com/graphhopper/reader/test-osm3.xml";

    @Test
    public void testLoadOSM() throws IOException {
        Helper.removeDir(new File(ghLoc));
        GraphHopper instance = new GraphHopper().setInMemory(true, true).
                encodingManager(new EncodingManager("CAR")).
                graphHopperLocation(ghLoc).osmFile(testOsm);
        instance.importOrLoad();
        GHResponse ph = instance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4));
        assertTrue(ph.found());
        assertEquals(3, ph.points().size());

        instance.close();
        instance = new GraphHopper().encodingManager(new EncodingManager("CAR")).setInMemory(true, true);
        assertTrue(instance.load(ghLoc));
        ph = instance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4));
        assertTrue(ph.found());
        assertEquals(3, ph.points().size());

        Helper.removeDir(new File(ghLoc));
    }

    @Test
    public void testPrepare() throws IOException {
        GraphHopper instance = new GraphHopper().setInMemory(true, false).
                encodingManager(new EncodingManager("CAR")).
                chShortcuts(true, true).
                graphHopperLocation(ghLoc).osmFile(testOsm);
        instance.importOrLoad();
        GHResponse ph = instance.route(new GHRequest(51.2492152, 9.4317166, 51.2, 9.4).algorithm("dijkstrabi"));
        assertTrue(ph.found());
        assertEquals(3, ph.points().size());
    }

    @Test
    public void testFootAndCar() throws IOException {
        // now all ways are imported
        GraphHopper instance = new GraphHopper().setInMemory(true, false).
                encodingManager(new EncodingManager("CAR,FOOT")).
                graphHopperLocation(ghLoc).osmFile(testOsm3);
        instance.importOrLoad();

        assertEquals(5, instance.graph().nodes());
        assertEquals(8, instance.graph().getAllEdges().maxId());

        // A to D
        GHResponse res = instance.route(new GHRequest(11.1, 50, 11.3, 51).vehicle(EncodingManager.CAR));
        assertTrue(res.found());
        assertEquals(2, res.points().size());
        // => found D
        assertEquals(51, res.points().longitude(1), 1e-3);
        assertEquals(11.3, res.points().latitude(1), 1e-3);

        // A to D not allowed for foot. But the location index will choose a node close to D accessible to FOOT        
        res = instance.route(new GHRequest(11.1, 50, 11.3, 51).vehicle(EncodingManager.FOOT));
        assertTrue(res.found());
        assertEquals(2, res.points().size());
        // => found B
        assertEquals(51, res.points().longitude(1), 1e-3);
        assertEquals(12, res.points().latitude(1), 1e-3);

        // A to E only for foot
        res = instance.route(new GHRequest(11.1, 50, 10, 51).vehicle(EncodingManager.FOOT));
        assertTrue(res.found());
        assertEquals(2, res.points().size());

        // A D E for car
        res = instance.route(new GHRequest(11.1, 50, 10, 51).vehicle(EncodingManager.CAR));
        assertTrue(res.found());
        assertEquals(3, res.points().size());
    }

    @Test
    public void testFailsForWrongConfig() throws IOException {
        GraphHopper instance = new GraphHopper().init(
                new CmdArgs().put("osmreader.acceptWay", "FOOT,CAR").put("osmreader.osm", testOsm3)).
                graphHopperLocation(ghLoc);
        instance.importOrLoad();
        assertEquals(5, instance.graph().nodes());

        instance = new GraphHopper().init(
                new CmdArgs().put("osmreader.acceptWay", "FOOT").put("osmreader.osm", testOsm3)).
                osmFile(testOsm3);
        try {
            instance.load(ghLoc);
            assertTrue(false);
        } catch (Exception ex) {
        }

        // different order should be ok
        instance = new GraphHopper().init(
                new CmdArgs().put("osmreader.acceptWay", "CAR,FOOT").put("osmreader.osm", testOsm3)).
                osmFile(testOsm3);
        assertTrue(instance.load(ghLoc));
        assertEquals(5, instance.graph().nodes());
    }

    @Test
    public void testFootOnly() throws IOException {
        // now only footable ways are imported => no A D C and B D E => the other both ways have pillar nodes!
        GraphHopper instance = new GraphHopper().setInMemory(true, false).
                encodingManager(new EncodingManager("FOOT")).
                graphHopperLocation(ghLoc).osmFile(testOsm3);
        instance.importOrLoad();

        assertEquals(2, instance.graph().nodes());
        assertEquals(2, instance.graph().getAllEdges().maxId());

        // A to E only for foot
        GHResponse res = instance.route(new GHRequest(11.1, 50, 11.2, 52).vehicle(EncodingManager.FOOT));
        assertTrue(res.found());
        assertEquals(3, res.points().size());
    }
}
