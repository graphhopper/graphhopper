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
package com.graphhopper.reader.dem;

import com.graphhopper.storage.DAType;
import com.graphhopper.util.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class SRTMProviderTest {
    SRTMProvider instance;

    @Before
    public void setUp() {
        instance = new SRTMProvider();
    }

    @After
    public void tearDown() {
        instance.release();
    }

    @Test
    public void testGetFileString() {
        assertEquals("Eurasia/N49E011", instance.getFileString(49, 11));
        assertEquals("Eurasia/N52W002", instance.getFileString(52.268157, -1.230469));
        assertEquals("Africa/S06E034", instance.getFileString(-5.965754, 34.804687));
        assertEquals("Australia/S29E131", instance.getFileString(-28.304381, 131.484375));
        assertEquals("South_America/S09W045", instance.getFileString(-9, -45));
        assertEquals("South_America/S10W046", instance.getFileString(-9.1, -45.1));
        assertEquals("South_America/S10W045", instance.getFileString(-9.6, -45));
        assertEquals("South_America/S28W071", instance.getFileString(-28, -71));
        assertEquals("South_America/S29W072", instance.getFileString(-28.88316, -71.070557));
    }

    @Test
    public void testGetHeight() throws IOException {
        instance.setCacheDir(new File("./files/"));
        // easy to verify orientation of tile:
//        instance.getEle(43, 13);

        // siegesturm
        assertEquals(466, instance.getEle(49.968651, 11.574869), 1e-1);
        // am main
        assertEquals(330, instance.getEle(49.958233, 11.558647), 1e-1);
        // south america
        assertEquals(1678, instance.getEle(-28.88316, -71.070557), 1e-1);
        assertEquals(0, instance.getEle(-28.671311, -71.38916), 1e-1);

        // montevideo
        // assertEquals(45, instance.getEle(-34.906205,-56.189575), 1e-1);
        // new york
        // assertEquals(21, instance.getEle(40.730348,-73.985882), 1e-1);
        // use 0 elevation if area not found
        assertEquals(0, instance.getEle(55.4711873, 19.2501641), 1e-1);

        assertEquals(161, instance.getEle(55.8943144, -3), 1e-1);
        // precision = 1e6 => -3
        // assertEquals(160, instance.getEle(55.8943144, -3.0000004), 1e-1);
        // precision = 1e7 => -4
        // assertEquals(161, instance.getEle(55.8943144, -3.0004), 1e-1);
        // assertEquals(161, instance.getEle(55.8943144, -3.0000001), 1e-1);
    }

    @Test
    public void testGetHeight_issue545() throws IOException {
        instance.setCacheDir(new File("./files/"));

        // test different precision of the elevation file (3600)
        assertEquals(84, instance.getEle(48.003878, -124.660492), 1e-1);
    }

    @Test
    public void testGetHeightMMap() throws IOException {
        instance.setCacheDir(new File("./files/"));
        assertEquals(161, instance.getEle(55.8943144, -3), 1e-1);
    }
}
