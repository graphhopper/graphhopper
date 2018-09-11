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
package com.graphhopper.matching;

import com.graphhopper.gpx.Trk;
import com.graphhopper.gpx.Trkpnt;
import com.graphhopper.util.GPXEntry;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Peter Karich
 */
public class TrkTest {

    @Test
    public void testDoImport() {
        Trk instance = Trk.doImport("./src/test/resources/test1.gpx");
        List<GPXEntry> res = instance.getEntries();
        assertEquals(264, res.size());

        assertEquals(new GPXEntry(51.377719, 12.338217, 0), res.get(0));
        assertEquals(new GPXEntry(51.371482, 12.363795, 235000), res.get(50));
    }

    @Test
    public void testDoImport2() {
        Trk instance = Trk.doImport("./src/test/resources/test2.gpx");
        List<GPXEntry> res = instance.getEntries();
        assertEquals(2, res.size());
    }

    @Test
    public void testDoImportNoMillis() {
        Trk instance = Trk.doImport("./src/test/resources/test2_no_millis.gpx");
        List<GPXEntry> res = instance.getEntries();
        assertEquals(3, res.size());
        assertEquals(51.377719, res.get(0).lat, 0.0);
        assertEquals(12.338217, res.get(0).lon, 0.0);
    }

}
