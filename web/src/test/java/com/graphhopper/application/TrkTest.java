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
package com.graphhopper.application;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.graphhopper.gpx.GpxConversions;
import com.graphhopper.jackson.Gpx;
import com.graphhopper.matching.Observation;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Peter Karich
 */
public class TrkTest {

    private XmlMapper xmlMapper = new XmlMapper();

    @Test
    public void test1() throws IOException {
        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/test1.gpx"), Gpx.class);
        List<Observation> gpxEntries = GpxConversions.getEntries(gpx.trk.get(0));
        assertEquals(264, gpxEntries.size());
        assertEquals(new Observation(new GHPoint(51.377719, 12.338217)), gpxEntries.get(0));
        assertEquals(new Observation(new GHPoint(51.371482, 12.363795)), gpxEntries.get(50));
    }

    @Test
    public void test2() throws IOException {
        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/test2.gpx"), Gpx.class);
        List<Observation> gpxEntries = GpxConversions.getEntries(gpx.trk.get(0));
        assertEquals(2, gpxEntries.size());
    }

    @Test
    public void test2NoMillis() throws IOException {
        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/test2_no_millis.gpx"), Gpx.class);
        List<Observation> gpxEntries = GpxConversions.getEntries(gpx.trk.get(0));
        assertEquals(3, gpxEntries.size());
        assertEquals(51.377719, gpxEntries.get(0).getPoint().lat, 0.0);
        assertEquals(12.338217, gpxEntries.get(0).getPoint().lon, 0.0);
    }

    @Test
    public void testNoTrk() throws IOException {
        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/no_trk.gpx"), Gpx.class);
        assertTrue(gpx.trk.isEmpty());
    }

    @Test
    public void testNoTrkseg() throws IOException {
        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/no_trkseg.gpx"), Gpx.class);
        assertTrue(GpxConversions.getEntries(gpx.trk.get(0)).isEmpty());
    }

    @Test
    public void testNoTrkpt() throws IOException {
        Gpx gpx = xmlMapper.readValue(getClass().getResourceAsStream("/no_trkpt.gpx"), Gpx.class);
        assertTrue(GpxConversions.getEntries(gpx.trk.get(0)).isEmpty());
    }

}
