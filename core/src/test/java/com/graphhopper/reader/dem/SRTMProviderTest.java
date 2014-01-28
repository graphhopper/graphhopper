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
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author Peter Karich
 */
public class SRTMProviderTest
{
    SRTMProvider instance;

    @Before
    public void setUp()
    {
        instance = new SRTMProvider();
    }

    @Test
    public void testGetFileString()
    {
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
    public void testGetHeight() throws IOException
    {
        instance.setCacheDir(new File("./files/"));
        // easy to verify orientation of tile:
//        instance.getHeight(43, 13);

        // siegesturm
        assertEquals(466, instance.getHeight(49.969331,11.574783));
        // am main
        assertEquals(330, instance.getHeight(49.958233,11.558647));
        // south america
        assertEquals(1691, instance.getHeight(-28.88316, -71.070557));
        assertEquals(0, instance.getHeight(-28.671311, -71.38916));
        
        // montevideo
        // assertEquals(45, instance.getHeight(-34.906205,-56.189575));        
        // new york
        // assertEquals(21, instance.getHeight(40.730348,-73.985882));        
    }
}
