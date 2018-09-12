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

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.graphhopper.matching.gpx.Gpx;
import com.graphhopper.util.GPXEntry;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Peter Karich
 */
public class TrkTest {

    private XmlMapper xmlMapper = new XmlMapper();

    @Test
    public void testDoImport() throws IOException {
        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/test1.gpx"), Gpx.class);
        List<GPXEntry> gpxEntries = gpx.trk.get(0).getEntries();
        assertEquals(264, gpxEntries.size());
        assertEquals(new GPXEntry(51.377719, 12.338217, 0), gpxEntries.get(0));
        assertEquals(new GPXEntry(51.371482, 12.363795, 235000), gpxEntries.get(50));
    }

    @Test
    public void testDoImport2() throws IOException {
        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/test2.gpx"), Gpx.class);
        List<GPXEntry> gpxEntries = gpx.trk.get(0).getEntries();
        assertEquals(2, gpxEntries.size());
    }

    @Test
    public void testDoImportNoMillis() throws IOException {
        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/test2_no_millis.gpx"), Gpx.class);
        List<GPXEntry> gpxEntries = gpx.trk.get(0).getEntries();
        assertEquals(3, gpxEntries.size());
        assertEquals(51.377719, gpxEntries.get(0).lat, 0.0);
        assertEquals(12.338217, gpxEntries.get(0).lon, 0.0);
    }

}
