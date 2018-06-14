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

import com.graphhopper.util.GPXEntry;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class GPXFileTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testDoImport() {
        GPXFile instance = new GPXFile();
        instance.doImport("./src/test/resources/test1.gpx");
        List<GPXEntry> res = instance.getEntries();
        assertEquals(264, res.size());

        assertEquals(new GPXEntry(51.377719, 12.338217, 0), res.get(0));
        assertEquals(new GPXEntry(51.371482, 12.363795, 235000), res.get(50));
    }

    @Test
    public void testDoImport2() {
        GPXFile instance = new GPXFile();
        instance.doImport("./src/test/resources/test2.gpx");
        List<GPXEntry> res = instance.getEntries();
        assertEquals(2, res.size());
    }

    @Test
    public void testDoImportNoMillis() {
        GPXFile instance = new GPXFile();
        instance.doImport("./src/test/resources/test2_no_millis.gpx");
        List<GPXEntry> res = instance.getEntries();
        assertEquals(3, res.size());
        assertEquals(0, res.get(0).getTime());
        assertEquals(18000, res.get(1).getTime(), 1000);
        assertEquals(32000, res.get(2).getTime(), 1000);
    }

    @Test
    public void testParseDate() throws ParseException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        assertEquals(1412700604000L, df.parse("2014-10-07T16:50:04+0000").getTime());
    }

    @Test
    public void testGPXWithoutTrackpoints() throws RuntimeException {
        GPXFile instance = new GPXFile();
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("java.text.ParseException: No trackpoints found in GPX file");
        instance.doImport("./src/test/resources/test-only-wpt.gpx");
    }
}
