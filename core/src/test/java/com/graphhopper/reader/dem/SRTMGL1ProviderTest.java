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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Robin Boldt
 */
public class SRTMGL1ProviderTest {
    private double precision = .1;
    SRTMGL1Provider instance;

    @BeforeEach
    public void setUp() {
        instance = new SRTMGL1Provider();
    }

    @AfterEach
    public void tearDown() {
        instance.release();
    }

    @Test
    public void testMinLat() {
        assertEquals(52, instance.getMinLatForTile(52.5));
        assertEquals(29, instance.getMinLatForTile(29.9));
        assertEquals(-60, instance.getMinLatForTile(-59.9));
    }

    @Test
    public void testMinLon() {
        assertEquals(-60, instance.getMinLonForTile(-59.9));
        assertEquals(0, instance.getMinLonForTile(0.9));
    }

    @Test
    public void testGetDownloadUrl() {
        // Created a couple of random tests and compared to https://topotools.cr.usgs.gov/gmted_viewer/viewer.htm
        assertEquals("North/North_30_60/N42E011.hgt", instance.getDownloadURL(42.940339, 11.953125));
        assertEquals("North/North_30_60/N38W078.hgt", instance.getDownloadURL(38.548165, -77.167969));
        assertEquals("North/North_0_29/N14W005.hgt", instance.getDownloadURL(14.116047, -4.277344));
        assertEquals("South/S52W058.hgt", instance.getDownloadURL(-51.015725, -57.621094));
        assertEquals("North/North_0_29/N24E120.hgt", instance.getDownloadURL(24.590108, 120.640625));
        assertEquals("South/S42W063.hgt", instance.getDownloadURL(-41.015725, -62.949219));
    }

    @Test
    public void testGetFileName() {
        assertEquals("n42e011", instance.getFileName(42.940339, 11.953125));
        assertEquals("n38w078", instance.getFileName(38.548165, -77.167969));
        assertEquals("n14w005", instance.getFileName(14.116047, -4.277344));
        assertEquals("s52w058", instance.getFileName(-51.015725, -57.621094));
        assertEquals("n24e120", instance.getFileName(24.590108, 120.640625));
        assertEquals("s42w063", instance.getFileName(-41.015725, -62.949219));
    }

    @Disabled
    @Test
    public void testGetEle() {
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
        // Outside of SRTM covered area
        assertEquals(0, instance.getEle(60.0000001, 16), precision);
        assertEquals(0, instance.getEle(60.0000001, 16), precision);
        assertEquals(0, instance.getEle(60.0000001, 19), precision);
        assertEquals(0, instance.getEle(60.251, 18.805), precision);
    }

    @Disabled
    @Test
    public void testGetEleVerticalBorder() {
        // Border between the tiles n42e011 and n43e011
        assertEquals("n42e011", instance.getFileName(42.999999, 11.48));
        assertEquals(420, instance.getEle(42.999999, 11.48), precision);
        assertEquals("n43e011", instance.getFileName(43.000001, 11.48));
        assertEquals(420, instance.getEle(43.000001, 11.48), precision);
    }

    @Disabled
    @Test
    public void testGetEleHorizontalBorder() {
        // Border between the tiles n42e011 and n42e012
        assertEquals("n42e011", instance.getFileName(42.1, 11.999999));
        assertEquals(324, instance.getEle(42.1, 11.999999), precision);
        assertEquals("n42e012", instance.getFileName(42.1, 12.000001));
        assertEquals(324, instance.getEle(42.1, 12.000001), precision);
    }

}
