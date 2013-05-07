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
package com.graphhopper.reader;

import com.graphhopper.routing.util.AcceptWay;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.storage.AbstractGraphTester;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the OSMReader with the normal helper initialized.
 *
 * @author Peter Karich
 */
public class OSMReaderTest {

    private String file1 = "test-osm.xml";
    private String file2 = "test-osm2.xml";
    private String file3 = "test-osm3.xml";
    private String file4 = "test-osm4.xml";
    private String dir = "./target/tmp/test-db";
    private CarFlagEncoder carEncoder = new CarFlagEncoder();
    private FootFlagEncoder footEncoder = new FootFlagEncoder();
    private EdgeFilter carOutFilter = new DefaultEdgeFilter(carEncoder, false, true);

    @Before public void setUp() {
        new File(dir).mkdirs();
    }

    @After public void tearDown() {
        Helper.removeDir(new File(dir));
    }

    GraphStorage buildGraph(String directory) {
        return new GraphStorage(new RAMDirectory(directory, false));
    }

    OSMReader init(OSMReader osmreader) {
        // make index creation fast
        // osmreader.indexCapacity(1000);
        return osmreader;
    }

    OSMReader preProcess(OSMReader osmreader, String file) {
        // default osmreader.setDoubleParse(true);
        osmreader.helper().preProcess(getClass().getResourceAsStream(file));
        return osmreader;
    }

    @Test public void testMain() {
        OSMReader reader = preProcess(init(new OSMReader(buildGraph(dir), 1000)), file1);
        reader.writeOsm2Graph(getClass().getResourceAsStream(file1));
        reader.optimize();
        reader.flush();
        reader.createIndex();
        Graph graph = reader.graph();
        assertEquals(4, graph.nodes());
        int n20 = AbstractGraphTester.getIdOf(graph, 52);
        int n10 = AbstractGraphTester.getIdOf(graph, 51.2492152);
        int n30 = AbstractGraphTester.getIdOf(graph, 51.2);
        int n50 = AbstractGraphTester.getIdOf(graph, 49);
        assertEquals(Arrays.asList(n20), GHUtility.neighbors(graph.getEdges(n10, carOutFilter)));
        assertEquals(3, GHUtility.count(graph.getEdges(n20, carOutFilter)));
        assertEquals(Arrays.asList(n20), GHUtility.neighbors(graph.getEdges(n30, carOutFilter)));

        EdgeIterator iter = graph.getEdges(n20, carOutFilter);
        assertTrue(iter.next());
        assertEquals(n10, iter.adjNode());
        assertEquals(88643, iter.distance(), 1);
        assertTrue(iter.next());
        assertEquals(n30, iter.adjNode());
        assertEquals(93147, iter.distance(), 1);
        CarFlagEncoder flags = carEncoder;
        assertTrue(flags.isMotorway(iter.flags()));
        assertTrue(flags.isForward(iter.flags()));
        assertTrue(flags.isBackward(iter.flags()));
        assertTrue(iter.next());
        assertEquals(n50, iter.adjNode());
        AbstractGraphTester.assertPList(Helper.createPointList(51.25, 9.43), iter.wayGeometry());
        assertTrue(flags.isService(iter.flags()));
        assertTrue(flags.isForward(iter.flags()));
        assertTrue(flags.isBackward(iter.flags()));

        // get third added location id=30
        iter = graph.getEdges(n30, carOutFilter);
        assertTrue(iter.next());
        assertEquals(n20, iter.adjNode());
        assertEquals(93146.888, iter.distance(), 1);

        assertEquals(9.4, graph.getLongitude(reader.location2IDIndex().findID(51.2, 9.4)), 1e-3);
        assertEquals(10, graph.getLongitude(reader.location2IDIndex().findID(49, 10)), 1e-3);
        assertEquals(51.249, graph.getLatitude(reader.location2IDIndex().findID(51.2492152, 9.4317166)), 1e-3);

        // node 40 is on the way between 30 and 50 => 9.0
        assertEquals(9, graph.getLongitude(reader.location2IDIndex().findID(51.25, 9.43)), 1e-3);
    }

    @Test public void testSort() {
        OSMReader reader = preProcess(init(new OSMReader(buildGraph(dir), 1000).sort(true)), file1);
        reader.writeOsm2Graph(getClass().getResourceAsStream(file1));
        reader.optimize();
        reader.flush();
        reader.createIndex();
        Graph graph = reader.graph();
        assertEquals(10, graph.getLongitude(reader.location2IDIndex().findID(49, 10)), 1e-3);
        assertEquals(51.249, graph.getLatitude(reader.location2IDIndex().findID(51.2492152, 9.4317166)), 1e-3);
    }

    @Test public void testWithBounds() {
        OSMReader reader = preProcess(init(new OSMReader(buildGraph(dir), 1000) {
            @Override public boolean isInBounds(double lat, double lon) {
                return lat > 49 && lon > 8;
            }
        }), file1);
        reader.writeOsm2Graph(getClass().getResourceAsStream(file1));
        reader.flush();
        Graph graph = reader.graph();
        assertEquals(4, graph.nodes());
        int n10 = AbstractGraphTester.getIdOf(graph, 51.2492152);
        int n20 = AbstractGraphTester.getIdOf(graph, 52);
        int n30 = AbstractGraphTester.getIdOf(graph, 51.2);
        int n40 = AbstractGraphTester.getIdOf(graph, 51.25);

        assertEquals(Arrays.asList(n20), GHUtility.neighbors(graph.getEdges(n10, carOutFilter)));
        assertEquals(3, GHUtility.count(graph.getEdges(n20, carOutFilter)));
        assertEquals(Arrays.asList(n20), GHUtility.neighbors(graph.getEdges(n30, carOutFilter)));

        EdgeIterator iter = graph.getEdges(n20, carOutFilter);
        assertTrue(iter.next());
        assertEquals(n10, iter.adjNode());
        assertEquals(88643, iter.distance(), 1);
        assertTrue(iter.next());
        assertEquals(n30, iter.adjNode());
        assertEquals(93146.888, iter.distance(), 1);
        assertTrue(iter.next());
        assertEquals(n40, iter.adjNode());
        AbstractGraphTester.assertPList(Helper.createPointList(), iter.wayGeometry());

        // get third added location => 2
        iter = graph.getEdges(n30, carOutFilter);
        assertTrue(iter.next());
        assertEquals(n20, iter.adjNode());
        assertEquals(93146.888, iter.distance(), 1);
        assertFalse(iter.next());
    }

    @Test public void testOneWay() {
        OSMReader reader = preProcess(init(new OSMReader(buildGraph(dir), 1000)), file2);
        reader.writeOsm2Graph(getClass().getResourceAsStream(file2));
        reader.flush();
        Graph graph = reader.graph();

        int n20 = AbstractGraphTester.getIdOf(graph, 52.0);
        int n10 = AbstractGraphTester.getIdOf(graph, 51.2492152);
        int n30 = AbstractGraphTester.getIdOf(graph, 51.2);
        assertEquals(1, GHUtility.count(graph.getEdges(n10, carOutFilter)));
        assertEquals(1, GHUtility.count(graph.getEdges(n20, carOutFilter)));
        assertEquals(0, GHUtility.count(graph.getEdges(n30, carOutFilter)));

        EdgeIterator iter = graph.getEdges(n20, carOutFilter);
        assertTrue(iter.next());
        assertEquals(n30, iter.adjNode());

        iter = graph.getEdges(n20);
        assertTrue(iter.next());
        assertEquals(n10, iter.adjNode());
        CarFlagEncoder encoder = carEncoder;
        assertTrue(encoder.isMotorway(iter.flags()));
        assertFalse(encoder.isForward(iter.flags()));
        assertTrue(encoder.isBackward(iter.flags()));

        assertTrue(iter.next());
        assertTrue(encoder.isMotorway(iter.flags()));
        assertTrue(encoder.isForward(iter.flags()));
        assertFalse(encoder.isBackward(iter.flags()));
    }

    @Test public void testFerry() {
        OSMReader reader = preProcess(init(new OSMReader(buildGraph(dir), 1000)), file2);
        reader.writeOsm2Graph(getClass().getResourceAsStream(file2));
        Graph graph = reader.graph();

        int n40 = AbstractGraphTester.getIdOf(graph, 54.0);
        int n50 = AbstractGraphTester.getIdOf(graph, 55.0);
        assertEquals(Arrays.asList(n40), GHUtility.neighbors(graph.getEdges(n50)));
    }

    @Test public void testMaxSpeed() {
        OSMReader reader = preProcess(init(new OSMReader(buildGraph(dir), 1000)), file2);
        reader.writeOsm2Graph(getClass().getResourceAsStream(file2));
        Graph graph = reader.graph();

        int n60 = AbstractGraphTester.getIdOf(graph, 56.0);
        EdgeIterator iter = graph.getEdges(n60);
        iter.next();
        assertEquals(40, carEncoder.getSpeed(iter.flags()));
    }

    @Test public void testWayReferencesNotExistingAdjNode() {
        OSMReader reader = preProcess(init(new OSMReader(buildGraph(dir), 1000).
                acceptStreet(new AcceptWay(true, false, true))), file4);
        reader.writeOsm2Graph(getClass().getResourceAsStream(file4));
        Graph graph = reader.graph();

        assertEquals(2, graph.nodes());
        int n10 = AbstractGraphTester.getIdOf(graph, 51.2492152);
        int n30 = AbstractGraphTester.getIdOf(graph, 51.2);

        assertEquals(Arrays.asList(n30), GHUtility.neighbors(graph.getEdges(n10)));
    }

    @Test public void testFoot() {
        OSMReader reader = preProcess(init(new OSMReader(buildGraph(dir), 1000).
                acceptStreet(new AcceptWay(true, false, true))), file3);
        reader.writeOsm2Graph(getClass().getResourceAsStream(file3));
        Graph graph = reader.graph();

        int n10 = AbstractGraphTester.getIdOf(graph, 11.1);
        int n20 = AbstractGraphTester.getIdOf(graph, 12);
        int n30 = AbstractGraphTester.getIdOf(graph, 11.2);
        int n40 = AbstractGraphTester.getIdOf(graph, 11.3);
        int n50 = AbstractGraphTester.getIdOf(graph, 10);

        assertEquals(Arrays.asList(n20, n40), GHUtility.neighbors(graph.getEdges(n10,
                new DefaultEdgeFilter(carEncoder))));
        assertEquals(new ArrayList<Integer>(), GHUtility.neighbors(graph.getEdges(n30,
                carOutFilter)));
        assertEquals(Arrays.asList(n10, n30, n40), GHUtility.neighbors(graph.getEdges(n20,
                new DefaultEdgeFilter(carEncoder))));
        assertEquals(Arrays.asList(n30, n40), GHUtility.neighbors(graph.getEdges(n20,
                carOutFilter)));

        EdgeFilter footOutFilter = new DefaultEdgeFilter(footEncoder, false, true);
        assertEquals(Arrays.asList(n20, n50), GHUtility.neighbors(graph.getEdges(n10,
                footOutFilter)));
        assertEquals(Arrays.asList(n20, n50), GHUtility.neighbors(graph.getEdges(n30,
                footOutFilter)));
        assertEquals(Arrays.asList(n10, n30), GHUtility.neighbors(graph.getEdges(n20,
                footOutFilter)));
    }
}
