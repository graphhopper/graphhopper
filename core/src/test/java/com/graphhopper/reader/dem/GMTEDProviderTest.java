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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Robin Boldt
 */
public class GMTEDProviderTest {
    private double precision = .1;
    GMTEDProvider instance;

    @BeforeEach
    public void setUp() {
        instance = new GMTEDProvider();
    }

    @AfterEach
    public void tearDown() {
        instance.release();
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
        assertTrue(instance.getDownloadURL(42.940339, 11.953125).contains("E000/30N000E_20101117_gmted_mea075.tif"));
        assertTrue(instance.getDownloadURL(38.548165, -77.167969).contains("W090/30N090W_20101117_gmted_mea075.tif"));
        assertTrue(instance.getDownloadURL(74.116047, -169.277344).contains("W180/70N180W_20101117_gmted_mea075.tif"));
        assertTrue(instance.getDownloadURL(-61.015725, -156.621094).contains("W180/70S180W_20101117_gmted_mea075.tif"));
        assertTrue(instance.getDownloadURL(74.590108, 166.640625).contains("E150/70N150E_20101117_gmted_mea075.tif"));
        assertTrue(instance.getDownloadURL(-61.015725, 162.949219).contains("E150/70S150E_20101117_gmted_mea075.tif"));
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

        // file not found
        assertTrue(file.exists());
        assertEquals(1048676, file.length());

        instance.setDownloader(new Downloader("test GH") {
            @Override
            public void downloadFile(String url, String toFile) throws IOException {
                throw new SocketTimeoutException("xyz");
            }
        });

        try {
            instance.setSleep(30);
            instance.getEle(16, -20);
            fail();
        } catch (Exception ex) {
        }

        file.delete();
        zipFile.delete();
    }

    /*
    Enabling this test requires you to change the pom.xml and increase the memory limit for running tests.
    Change to: <argLine>-Xmx500m -Xms500m</argLine>
    This test will download about 2gb of data.
     */
    @Disabled
    @Test
    public void testGetEle() {
        assertEquals(339, instance.getEle(49.949784, 11.57517), precision);
        assertEquals(438, instance.getEle(49.968668, 11.575127), precision);
        assertEquals(432, instance.getEle(49.968682, 11.574842), precision);
        assertEquals(3169, instance.getEle(-22.532854, -65.110474), precision);
        assertEquals(124, instance.getEle(38.065392, -87.099609), precision);
        assertEquals(1615, instance.getEle(40, -105.2277023), precision);
        assertEquals(1618, instance.getEle(39.99999999, -105.2277023), precision);
        assertEquals(1618, instance.getEle(39.9999999, -105.2277023), precision);
        assertEquals(1618, instance.getEle(39.999999, -105.2277023), precision);
        assertEquals(1070, instance.getEle(47.468668, 14.575127), precision);
        assertEquals(1115, instance.getEle(47.467753, 14.573911), precision);
        assertEquals(1990, instance.getEle(46.468835, 12.578777), precision);
        assertEquals(841, instance.getEle(48.469123, 9.576393), precision);
        assertEquals(0, instance.getEle(56.4787319, 17.6118363), precision);
        assertEquals(0, instance.getEle(56.4787319, 17.6118363), precision);
        // Outside of SRTM covered area
        assertEquals(108, instance.getEle(60.0000001, 16), precision);
        assertEquals(0, instance.getEle(60.0000001, 19), precision);
        // Stor Roten
        assertEquals(14, instance.getEle(60.251, 18.805), precision);

    }

    @Disabled
    @Test
    public void testGetEleVerticalBorder() {
        // Border between the tiles 50n000e and 70n000e
        assertEquals("50n000e_20101117_gmted_mea075", instance.getFileName(69.999999, 19.493));
        assertEquals(268, instance.getEle(69.999999, 19.5249), precision);
        assertEquals("70n000e_20101117_gmted_mea075", instance.getFileName(70, 19.493));
        assertEquals(298, instance.getEle(70, 19.5249), precision);
        // Second location at the border
        assertEquals("50n000e_20101117_gmted_mea075", instance.getFileName(69.999999, 19.236));
        assertEquals(245, instance.getEle(69.999999, 19.236), precision);
        assertEquals("70n000e_20101117_gmted_mea075", instance.getFileName(70, 19.236));
        assertEquals(241, instance.getEle(70, 19.236), precision);
    }

    @Disabled
    @Test
    public void testGetEleHorizontalBorder() {
        // Border between the tiles 50n000e and 50n030e
        assertEquals("50n000e_20101117_gmted_mea075", instance.getFileName(53, 29.999999));
        assertEquals(143, instance.getEle(53, 29.999999), precision);
        assertEquals("50n030e_20101117_gmted_mea075", instance.getFileName(53, 30.000001));
        assertEquals(142, instance.getEle(53, 30.000001), precision);
    }
}
