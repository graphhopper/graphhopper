/*
 * Copyright 2013 Thomas Buerli <tbuerli@student.ethz.ch>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.reader;

import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.EdgePropertyEncoder;
import com.graphhopper.routing.util.PublicTransitFlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.index.LocationTime2IDIndex;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Thomas Buerli <tbuerli@student.ethz.ch>
 */
public class GTFSReaderTest {

    private String file1 = "test-gtfs1.zip";
    private String file2 = "test-gtfs2.zip";

    public GTFSReaderTest() {
    }

    private File getFile(String file) throws URISyntaxException {
        return new File(getClass().getResource(file).toURI());
    }

    private GTFSReader readGTFS(String testFile) {
        GraphStorage graph = new GraphBuilder().create();
        GTFSReader gtfsReader = new GTFSReader(graph);
        try {
            File gtfsFile = getFile(testFile);
            gtfsReader.load(gtfsFile);
            gtfsReader.close();
        } catch (URISyntaxException ex) {
            fail("Could not open " + file2 + ": " + ex);
        } catch (IOException ex) {
            fail("Could not load GTFS file " + file2 + ": " + ex);
        }
        
        return gtfsReader;
    }

    @Test
    public void testReader2() {
        GTFSReader reader = readGTFS(file2);
        Graph graph = reader.graph();
        // 2 * Number of stops + 2 * number of stoptimes
        // 2 * number of trips (1 for start & 1 for end)
        // 2 for each stop in a trip (excluding start and end)
        //
        // 2 * 9 + 2 * 38 + + 2 * 12 + 2 * 12
        assertEquals(142, graph.nodes());
    }

    @Test
    public void testReader1() {
        GTFSReader reader = readGTFS(file1);
        Graph graph = reader.graph();

        // 2* 9 + 2 * 28 + 2 * 12
        assertEquals(90, graph.nodes());
    }
    
    /**
     * Test if the right start position is found for a given location and time
     */
    @Test
    public void testIndex() {
        GTFSReader reader = readGTFS(file1);
        LocationTime2IDIndex index = reader.getIndex();
        assertEquals(0, index.findID(36.915682,-116.751677, 0));
        assertEquals(0, index.findID(36.915682,-116.751677, 21000));
        assertEquals(5, index.findID(36.915682,-116.751677, 21600));
        assertEquals(68, index.findID(36.425288,-117.133162, 0));
        assertEquals(73, index.findID(36.425288,-117.133162, 33600));
        assertEquals(72, index.findID(36.425288,-117.133162, 33960));
        assertEquals(72, index.findID(36.425288,-117.133162, 34000));
    }
    
    /**
     * Tests if the right exit node for a station is found
     */
    @Test
    public void testExitNode() {
        GTFSReader reader = readGTFS(file1);
        LocationTime2IDIndex index = reader.getIndex();
        assertEquals(1,index.getExitNodeID(36.915682,-116.751677));
        assertEquals(69,index.getExitNodeID(36.425288,-117.133162));
    }
    
    /**
     * 
     */
    @Test
    public void testRouting() {
        GTFSReader reader = readGTFS(file1);
        Graph graph = reader.graph();
        LocationTime2IDIndex index = reader.getIndex();
        EdgePropertyEncoder encoder = new PublicTransitFlagEncoder();
        RoutingAlgorithm algorithm = new Dijkstra(graph, encoder);
        // Start BEATTY_AIRPORT at midnight to Amargosa Valley
        int from = index.findID(36.868446,-116.784582, 0);
        int to = index.getExitNodeID(36.641496,-116.40094);
        Path p1 = algorithm.calcPath(from, to);
        assertEquals(p1.toString(), 32760, p1.distance(), 1e-6);
        //reader.debugPath(p1);
    }
}