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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Karich
 */
public class SRTMProviderTest {
    private double precision = .1;
    SRTMProvider instance;

    @BeforeEach
    public void setUp() {
        instance = new SRTMProvider();
    }

    @AfterEach
    public void tearDown() {
        instance.release();
    }

    @Test
    public void testGetFileString() {
        assertEquals("Eurasia/N49E011", instance.getFileName(49, 11));
        assertEquals("Eurasia/N52W002", instance.getFileName(52.268157, -1.230469));
        assertEquals("Africa/S06E034", instance.getFileName(-5.965754, 34.804687));
        assertEquals("Australia/S29E131", instance.getFileName(-28.304381, 131.484375));
        assertEquals("South_America/S09W045", instance.getFileName(-9, -45));
        assertEquals("South_America/S10W046", instance.getFileName(-9.1, -45.1));
        assertEquals("South_America/S10W045", instance.getFileName(-9.6, -45));
        assertEquals("South_America/S28W071", instance.getFileName(-28, -71));
        assertEquals("South_America/S29W072", instance.getFileName(-28.88316, -71.070557));
    }

    @Test
    public void testGetHeight() throws IOException {
        instance = new SRTMProvider("./files/");
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
        instance = new SRTMProvider("./files/");

        // test different precision of the elevation file (3600)
        assertEquals(84, instance.getEle(48.003878, -124.660492), 1e-1);
    }

    @Test
    public void testGetHeightMMap() throws IOException {
        instance = new SRTMProvider("./files/");
        assertEquals(161, instance.getEle(55.8943144, -3), 1e-1);
    }

    @Disabled
    @Test
    public void testGetEle() {
        instance = new SRTMProvider();
        assertEquals(337, instance.getEle(49.949784, 11.57517), precision);
        assertEquals(466, instance.getEle(49.968668, 11.575127), precision);
        assertEquals(466, instance.getEle(49.968682, 11.574842), precision);
        assertEquals(3100, instance.getEle(-22.532854, -65.110474), precision);
        assertEquals(122, instance.getEle(38.065392, -87.099609), precision);
        assertEquals(1617, instance.getEle(40, -105.2277023), precision);
        assertEquals(1617, instance.getEle(39.99999999, -105.2277023), precision);
        assertEquals(1617, instance.getEle(39.9999999, -105.2277023), precision);
        assertEquals(1617, instance.getEle(39.999999, -105.2277023), precision);
        assertEquals(1046, instance.getEle(47.468668, 14.575127), precision);
        assertEquals(1113, instance.getEle(47.467753, 14.573911), precision);
        assertEquals(1946, instance.getEle(46.468835, 12.578777), precision);
        assertEquals(845, instance.getEle(48.469123, 9.576393), precision);
        assertEquals(0, instance.getEle(56.4787319, 17.6118363), precision);
        assertEquals(0, instance.getEle(56.4787319, 17.6118363), precision);
        // Outside of SRTM covered area
        assertEquals(0, instance.getEle(60.0000001, 16), precision);
        assertEquals(0, instance.getEle(60.0000001, 16), precision);
        assertEquals(0, instance.getEle(60.0000001, 19), precision);
        assertEquals(0, instance.getEle(60.251, 18.805), precision);
    }

    @Disabled
    @Test
    public void testGetEleVerticalBorder() {
        instance = new SRTMProvider();
        // Border between the tiles N42E011 and N43E011
        assertEquals("Eurasia/N42E011", instance.getFileName(42.999999, 11.48));
        assertEquals(419, instance.getEle(42.999999, 11.48), precision);
        assertEquals("Eurasia/N43E011", instance.getFileName(43.000001, 11.48));
        assertEquals(419, instance.getEle(43.000001, 11.48), precision);
    }

    @Disabled
    @Test
    public void testGetEleHorizontalBorder() {
        instance = new SRTMProvider();
        // Border between the tiles N42E011 and N42E012
        assertEquals("Eurasia/N42E011", instance.getFileName(42.1, 11.999999));
        assertEquals(324, instance.getEle(42.1, 11.999999), precision);
        assertEquals("Eurasia/N42E012", instance.getFileName(42.1, 12.000001));
        assertEquals(324, instance.getEle(42.1, 12.000001), precision);
    }

    @Disabled
    @Test
    public void testDownloadIssue_1274() {
        instance = new SRTMProvider();
        // The file is incorrectly named on the sever: N55W061hgt.zip (it should be N55W061.hgt.zip)
        assertEquals("North_America/N55W061", instance.getFileName(55.055,-60.541));
        assertEquals(204, instance.getEle(55.055,-60.541), .1);
    }

}
