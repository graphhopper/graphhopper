/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.GPXEntry;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.Translation;
import com.graphhopper.util.shapes.GHPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class MapMatchingTest {

    // enable turn cost in encoder:
    private static final CarFlagEncoder encoder = new CarFlagEncoder(5, 5, 3);
    private static final TestGraphHopper hopper = new TestGraphHopper();

    @BeforeClass
    public static void doImport() {
        hopper.setOSMFile("./map-data/leipzig_germany.osm.pbf");
        hopper.setGraphHopperLocation("./target/mapmatchingtest");
        hopper.setEncodingManager(new EncodingManager(encoder));
        hopper.setCHEnable(false);
        // hopper.clean();
        hopper.importOrLoad();
    }

    @AfterClass
    public static void doClose() {
        hopper.close();
    }

    @Test
    public void testDoWork() {
        GraphStorage graph = hopper.getGraph();

        LocationIndexMatch locationIndex = new LocationIndexMatch(graph,
                (LocationIndexTree) hopper.getLocationIndex());
        
        MapMatching mapMatching = new MapMatching(graph, locationIndex, encoder);

        // sub path
        List<GPXEntry> inputGPXEntries = createRandomGPXEntries(
                new GHPoint(51.358735, 12.360574),
                new GHPoint(51.358594, 12.360032));
        MatchResult mr = mapMatching.doWork(inputGPXEntries);
        // create street names
        assertEquals(Arrays.asList("Platnerstraße", "Platnerstraße", "Platnerstraße"),
                fetchStreets(mr.getEdgeMatches()));
        assertEquals(mr.getGpxEntriesLength(), mr.getMatchLength(), .1);
        assertEquals(mr.getGpxEntriesMillis(), mr.getMatchMillis());

        inputGPXEntries = createRandomGPXEntries(
                new GHPoint(51.33099, 12.380267),
                new GHPoint(51.330689, 12.380776));
        mr = mapMatching.doWork(inputGPXEntries);
        // mr = new GPXFile(mr).doExport("test.gpx");
        assertEquals(5, mr.getEdgeMatches().size());
        assertEquals(Arrays.asList("Windmühlenstraße", "Windmühlenstraße",
                "Bayrischer Platz", "Bayrischer Platz", "Bayrischer Platz"),
                fetchStreets(mr.getEdgeMatches()));
        assertEquals(mr.getGpxEntriesLength(), mr.getMatchLength(), .1);
        assertEquals(mr.getGpxEntriesMillis(), mr.getMatchMillis());

        // full path
        inputGPXEntries = createRandomGPXEntries(
                new GHPoint(51.377781, 12.338333),
                new GHPoint(51.323317, 12.387085));
        mapMatching = new MapMatching(graph, locationIndex, encoder);
        new GPXFile(inputGPXEntries).doExport("test-input.gpx");
        mr = mapMatching.doWork(inputGPXEntries);
        new GPXFile(mr).doExport("test.gpx");

        // System.out.println(fetchStreets(mr.getEdgeMatches()));
        assertEquals(mr.getGpxEntriesLength(), mr.getMatchLength(), 0.5);
        assertEquals(mr.getGpxEntriesMillis(), mr.getMatchMillis());
        assertEquals(138, mr.getEdgeMatches().size());

        // TODO full path with 20m distortion
        // TODO full path with 40m distortion
    }

    List<String> fetchStreets(List<EdgeMatch> emList) {
        List<String> list = new ArrayList<String>();
        for (EdgeMatch em : emList) {
            list.add(em.getEdgeState().getName());
        }
        return list;
    }

    private List<GPXEntry> createRandomGPXEntries(GHPoint start, GHPoint end) {
        hopper.route(new GHRequest(start, end).setWeighting("fastest"));
        return hopper.getEdges(0);
    }

    // use a workaround to get access to 
    static class TestGraphHopper extends GraphHopper {

        private List<Path> paths;

        List<GPXEntry> getEdges(int index) {
            Path path = paths.get(index);
            Translation tr = getTranslationMap().get("en");
            InstructionList instr = path.calcInstructions(tr);
            // GPXFile.write(path, "calculated-route.gpx", tr);
            return instr.createGPXList();
        }

        @Override
        protected List<Path> getPaths(GHRequest request, GHResponse rsp) {
            paths = super.getPaths(request, rsp);
            return paths;
        }
    }
}
