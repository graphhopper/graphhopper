/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
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
package com.graphhopper.reader.dem;

import java.io.File;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class CGIARProviderTest
{
    @Test
    public void testDown()
    {
        CGIARProvider instance = new CGIARProvider();
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
    public void testFileName()
    {
        CGIARProvider instance = new CGIARProvider();
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
    public void testFileNotFound()
    {
        CGIARProvider instance = new CGIARProvider();
        File file = new File(instance.getCacheDir(), instance.getFileName(46, -20) + ".gh");
        File zipFile = new File(instance.getCacheDir(), instance.getFileName(46, -20) + ".zip");
        file.delete();
        zipFile.delete();
        
        assertEquals(0, instance.getEle(46, -20), 1);

        // file not found => small!
        assertTrue(file.exists());
        assertEquals(228, file.length());        
        file.delete();
        zipFile.delete();
    }
}
