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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Robin Boldt
 */
public class MultiSourceElevationProviderTest {
    MultiSourceElevationProvider instance;

    @AfterEach
    public void tearDown() {
        instance.release();
    }

    @Test
    public void testGetEleMocked() {
        instance = new MultiSourceElevationProvider(
                new CGIARProvider() {
                    @Override
                    public double getEle(double lat, double lon) {
                        return 1;
                    }
                },
                new GMTEDProvider() {
                    @Override
                    public double getEle(double lat, double lon) {
                        return 2;
                    }
                }
        );

        assertEquals(1, instance.getEle(0, 0), .1);
        assertEquals(2, instance.getEle(60.0001, 0), .1);
        assertEquals(2, instance.getEle(-56.0001, 0), .1);
    }

    /*
    Enabling this test requires you to change the pom.xml and increase the memory limit for running tests.
    Change to: <argLine>-Xmx500m -Xms500m</argLine>
    */
    @Disabled
    @Test
    public void testGetEle() {
        instance = new MultiSourceElevationProvider();
        double precision = .1;
        // The first part is copied from the SRTMGL1ProviderTest
        assertEquals(338, instance.getEle(49.949784, 11.57517), precision);
        assertEquals(468, instance.getEle(49.968668, 11.575127), precision);
        assertEquals(467, instance.getEle(49.968682, 11.574842), precision);
        assertEquals(3110, instance.getEle(-22.532854, -65.110474), precision);
        assertEquals(120, instance.getEle(38.065392, -87.099609), precision);
        assertEquals(1617, instance.getEle(40, -105.2277023), precision);
        assertEquals(1617, instance.getEle(39.99999999, -105.2277023), precision);
        assertEquals(1617, instance.getEle(39.9999999, -105.2277023), precision);
        assertEquals(1617, instance.getEle(39.999999, -105.2277023), precision);
        assertEquals(1015, instance.getEle(47.468668, 14.575127), precision);
        assertEquals(1107, instance.getEle(47.467753, 14.573911), precision);
        assertEquals(1930, instance.getEle(46.468835, 12.578777), precision);
        assertEquals(844, instance.getEle(48.469123, 9.576393), precision);
        // The file for this coordinate does not exist, but there is a ferry tagged in OSM
        assertEquals(0, instance.getEle(56.4787319, 17.6118363), precision);
        assertEquals(0, instance.getEle(56.4787319, 17.6118363), precision);
        // The second part is copied from the GMTEDProviderTest
        // Outside of SRTM covered area
        assertEquals(108, instance.getEle(60.0000001, 16), precision);
        assertEquals(0, instance.getEle(60.0000001, 19), precision);
        // Stor Roten
        assertEquals(14, instance.getEle(60.251, 18.805), precision);
    }
}
