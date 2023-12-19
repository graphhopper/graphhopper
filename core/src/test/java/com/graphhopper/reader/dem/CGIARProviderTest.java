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
 * @author Peter Karich
 */
public class CGIARProviderTest {
    private double precision = .1;
    CGIARProvider instance;

    @BeforeEach
    public void setUp() {
        instance = new CGIARProvider();
    }

    @AfterEach
    public void tearDown() {
        instance.release();
    }

    @Test
    public void testDown() {
        assertEquals(50, instance.down(52.5));
        assertEquals(0, instance.down(0.1));
        assertEquals(0, instance.down(0.01));
        assertEquals(-5, instance.down(-0.01));
        assertEquals(-5, instance.down(-2));
        assertEquals(-10, instance.down(-5.1));
        assertEquals(50, instance.down(50));
        assertEquals(45, instance.down(49));
    }

    @Test
    public void testFileName() {
        assertEquals("srtm_36_02", instance.getFileName(52, -0.1));
        assertEquals("srtm_35_02", instance.getFileName(50, -10));

        assertEquals("srtm_36_23", instance.getFileName(-52, -0.1));
        assertEquals("srtm_35_22", instance.getFileName(-50, -10));

        assertEquals("srtm_39_03", instance.getFileName(49.9, 11.5));
        assertEquals("srtm_34_08", instance.getFileName(20, -11));
        assertEquals("srtm_34_08", instance.getFileName(20, -14));
        assertEquals("srtm_34_08", instance.getFileName(20, -15));
        assertEquals("srtm_37_02", instance.getFileName(52.1943832, 0.1363176));
    }

    @Test
    public void testFileNotFound() {
        File file = new File(instance.getCacheDir(), instance.getFileName(46, -20) + ".gh");
        File zipFile = new File(instance.getCacheDir(), instance.getFileName(46, -20) + ".zip");
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

    @Disabled
    @Test
    public void testGetEle() {
        assertEquals(337, instance.getEle(49.949784, 11.57517), precision);
        assertEquals(466, instance.getEle(49.968668, 11.575127), precision);
        assertEquals(455, instance.getEle(49.968682, 11.574842), precision);
        assertEquals(3134, instance.getEle(-22.532854, -65.110474), precision);
        assertEquals(120, instance.getEle(38.065392, -87.099609), precision);
        assertEquals(1615, instance.getEle(40, -105.2277023), precision);
        assertEquals(1615, instance.getEle(39.99999999, -105.2277023), precision);
        assertEquals(1615, instance.getEle(39.9999999, -105.2277023), precision);
        assertEquals(1616, instance.getEle(39.999999, -105.2277023), precision);
        assertEquals(986, instance.getEle(47.468668, 14.575127), precision);
        assertEquals(1091, instance.getEle(47.467753, 14.573911), precision);
        assertEquals(1951, instance.getEle(46.468835, 12.578777), precision);
        assertEquals(841, instance.getEle(48.469123, 9.576393), precision);
        assertEquals(Double.NaN, instance.getEle(56.4787319, 17.6118363), precision);
        // Outside of SRTM covered area
        assertEquals(0, instance.getEle(60.0000001, 16), precision);
        assertEquals(0, instance.getEle(60.0000001, 16), precision);
        assertEquals(0, instance.getEle(60.0000001, 19), precision);
        assertEquals(0, instance.getEle(60.251, 18.805), precision);
    }

    @Disabled
    @Test
    public void testGetEleVerticalBorder() {
        // Border between the tiles srtm_39_04 and srtm_39_03
        assertEquals("srtm_39_04", instance.getFileName(44.999999, 11.5));
        assertEquals(5, instance.getEle(44.999999, 11.5), precision);
        assertEquals("srtm_39_03", instance.getFileName(45.000001, 11.5));
        assertEquals(6, instance.getEle(45.000001, 11.5), precision);
    }

    @Disabled
    @Test
    public void testGetEleHorizontalBorder() {
        // Border between the tiles N42E011 and N42E012
        assertEquals("srtm_38_04", instance.getFileName(44.94, 9.999999));
        assertEquals(48, instance.getEle(44.94, 9.999999), precision);
        assertEquals("srtm_39_04", instance.getFileName(44.94, 10.000001));
        assertEquals(48, instance.getEle(44.94, 10.000001), precision);
    }
}
