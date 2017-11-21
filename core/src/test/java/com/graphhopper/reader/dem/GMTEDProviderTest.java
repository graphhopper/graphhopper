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

import com.graphhopper.util.Downloader;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Robin Boldt
 */
public class GMTEDProviderTest {
    GMTEDProvider instance;

    @Before
    public void setUp() {
        instance = new GMTEDProvider();
    }

    @Test
    public void testMinLat() {
        assertEquals(50, instance.getMinLatForTile(52.5));
        assertEquals(10, instance.getMinLatForTile(29.9));
        assertEquals(-70, instance.getMinLatForTile(-59.9));
    }

    @Test
    public void testMinLon() {
        assertEquals(-60, instance.getMinLonForTile(-59.9));
        assertEquals(0, instance.getMinLonForTile(0.9));
    }

    @Test
    public void testGetDownloadUrl() {
        // Created a couple of random tests and compared to https://topotools.cr.usgs.gov/gmted_viewer/viewer.htm
        assertEquals("E000/30N000E_20101117_gmted_mea075.tif", instance.getDownloadURL(42.940339, 11.953125));
        assertEquals("W090/30N090W_20101117_gmted_mea075.tif", instance.getDownloadURL(38.548165, -77.167969));
        assertEquals("W180/70N180W_20101117_gmted_mea075.tif", instance.getDownloadURL(74.116047, -169.277344));
        assertEquals("W180/70S180W_20101117_gmted_mea075.tif", instance.getDownloadURL(-61.015725, -156.621094));
        assertEquals("E150/70N150E_20101117_gmted_mea075.tif", instance.getDownloadURL(74.590108, 166.640625));
        assertEquals("E150/70S150E_20101117_gmted_mea075.tif", instance.getDownloadURL(-61.015725, 162.949219));
    }

    @Test
    public void testGetFileName() {
        assertEquals("30n000e_20101117_gmted_mea075", instance.getFileName(42.940339, 11.953125));
        assertEquals("30n090w_20101117_gmted_mea075", instance.getFileName(38.548165, -77.167969));
        assertEquals("70n180w_20101117_gmted_mea075", instance.getFileName(74.116047, -169.277344));
        assertEquals("70s180w_20101117_gmted_mea075", instance.getFileName(-61.015725, -156.621094));
        assertEquals("70n150e_20101117_gmted_mea075", instance.getFileName(74.590108, 166.640625));
        assertEquals("70s150e_20101117_gmted_mea075", instance.getFileName(-61.015725, 162.949219));
    }

    @Test
    public void testFileNotFound() {
        File file = new File(instance.getCacheDir(), instance.getFileName(46, -20) + ".gh");
        File zipFile = new File(instance.getCacheDir(), instance.getFileName(46, -20) + ".tif");
        file.delete();
        zipFile.delete();

        instance.setDownloader(new Downloader("test GH") {
            @Override
            public void downloadFile(String url, String toFile) throws IOException {
                throw new FileNotFoundException("xyz");
            }
        });
        assertEquals(0, instance.getEle(46, -20), 1);

        // file not found => small!
        assertTrue(file.exists());
        assertEquals(228, file.length());

        instance.setDownloader(new Downloader("test GH") {
            @Override
            public void downloadFile(String url, String toFile) throws IOException {
                throw new SocketTimeoutException("xyz");
            }
        });

        try {
            instance.setSleep(30);
            instance.getEle(16, -20);
            assertTrue(false);
        } catch (Exception ex) {
        }

        file.delete();
        zipFile.delete();
    }
}