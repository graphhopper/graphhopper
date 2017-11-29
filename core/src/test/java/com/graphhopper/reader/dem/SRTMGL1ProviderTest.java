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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Robin Boldt
 */
public class SRTMGL1ProviderTest {
    SRTMGL1Provider instance;

    @Before
    public void setUp() {
        instance = new SRTMGL1Provider();
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

}