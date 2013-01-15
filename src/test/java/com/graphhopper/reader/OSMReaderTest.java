/*
 *  Copyright 2012 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

import com.graphhopper.routing.util.CarStreetType;
import com.graphhopper.storage.AbstractGraphTester;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GraphUtility;
import com.graphhopper.util.Helper;
import java.io.File;
import java.util.Arrays;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the OSMReader with the normal helper initialized.
 *
 * @author Peter Karich,
 */
public class OSMReaderTest {

    private String dir = "./target/tmp/test-db";

    @Before public void setUp() {
        new File(dir).mkdirs();
    }

    @After public void tearDown() {
        Helper.deleteDir(new File(dir));
    }

    GraphStorage createGraph(String directory) {
        return new GraphStorage(new RAMDirectory(dir, false));
    }

    OSMReader init(OSMReader osmreader) {
        // make index creation fast
        osmreader.setIndexCapacity(1000);
        return osmreader;
    }

    OSMReader preProcess(OSMReader osmreader) {
        osmreader.setDoubleParse(true);
        osmreader.getHelper().preProcess(getClass().getResourceAsStream("test-osm.xml"));
        return osmreader;
    }

    @Test public void testMain() {
        OSMReader reader = preProcess(init(new OSMReader(createGraph(dir), 1000)));
        reader.writeOsm2Graph(getClass().getResourceAsStream("test-osm.xml"));
        reader.optimize();
        reader.flush();
        Graph graph = reader.getGraph();
        assertEquals(4, graph.getNodes());
        int internalIdMain = AbstractGraphTester.getIdOf(graph, 52);
        int internalId1 = AbstractGraphTester.getIdOf(graph, 51.2492152);
        int internalId2 = AbstractGraphTester.getIdOf(graph, 51.2);
        int internalId3 = AbstractGraphTester.getIdOf(graph, 49);
        assertEquals(Arrays.asList(internalIdMain), GraphUtility.neighbors(graph.getOutgoing(internalId1)));
        assertEquals(3, GraphUtility.count(graph.getOutgoing(internalIdMain)));
        assertEquals(Arrays.asList(internalIdMain), GraphUtility.neighbors(graph.getOutgoing(internalId2)));

        EdgeIterator iter = graph.getOutgoing(internalIdMain);
        assertTrue(iter.next());
        assertEquals(internalId1, iter.node());
        assertEquals(88643, iter.distance(), 1);
        assertTrue(iter.next());
        assertEquals(internalId2, iter.node());
        assertEquals(93147, iter.distance(), 1);
        CarStreetType flags = new CarStreetType(iter.flags());
        assertTrue(flags.isMotorway());
        assertTrue(flags.isForward());
        assertTrue(flags.isBackward());
        assertTrue(iter.next());
        assertEquals(internalId3, iter.node());
        AbstractGraphTester.assertPList(Helper.createPointList(51.25, 9.43), iter.pillarNodes());
        flags = new CarStreetType(iter.flags());
        assertTrue(flags.isService());
        assertTrue(flags.isForward());
        assertTrue(flags.isBackward());

        // get third added location id=30
        iter = graph.getOutgoing(internalId2);
        assertTrue(iter.next());
        assertEquals(internalIdMain, iter.node());
        assertEquals(93146.888, iter.distance(), 1);

        assertEquals(9.431, graph.getLongitude(reader.getLocation2IDIndex().findID(51.25, 9.43)), 1e-3);
        assertEquals(9.4, graph.getLongitude(reader.getLocation2IDIndex().findID(51.2, 9.4)), 1e-3);
        assertEquals(10, graph.getLongitude(reader.getLocation2IDIndex().findID(49, 10)), 1e-3);
        assertEquals(51.249, graph.getLatitude(reader.getLocation2IDIndex().findID(51.2492152, 9.4317166)), 1e-3);
    }

    @Test public void testSort() {
        OSMReader reader = preProcess(init(new OSMReader(createGraph(dir), 1000).setSort(true)));
        reader.writeOsm2Graph(getClass().getResourceAsStream("test-osm.xml"));
        reader.optimize();
        reader.flush();
        Graph graph = reader.getGraph();
        assertEquals(10, graph.getLongitude(reader.getLocation2IDIndex().findID(49, 10)), 1e-3);
        assertEquals(51.249, graph.getLatitude(reader.getLocation2IDIndex().findID(51.2492152, 9.4317166)), 1e-3);
    }

    @Test public void testWithBounds() {
        OSMReader reader = preProcess(init(new OSMReader(createGraph(dir), 1000) {
            @Override public boolean isInBounds(double lat, double lon) {
                return lat > 49 && lon > 8;
            }
        }));
        reader.writeOsm2Graph(getClass().getResourceAsStream("test-osm.xml"));
        reader.flush();
        Graph graph = reader.getGraph();
        assertEquals(4, graph.getNodes());
        int internalId1 = AbstractGraphTester.getIdOf(graph, 51.2492152); // 10
        int internalIdMain = AbstractGraphTester.getIdOf(graph, 52); // 20
        int internalId2 = AbstractGraphTester.getIdOf(graph, 51.2);  // 30
        int internalId4 = AbstractGraphTester.getIdOf(graph, 51.25); // 40

        assertEquals(Arrays.asList(internalIdMain), GraphUtility.neighbors(graph.getOutgoing(internalId1)));
        assertEquals(3, GraphUtility.count(graph.getOutgoing(internalIdMain)));
        assertEquals(Arrays.asList(internalIdMain), GraphUtility.neighbors(graph.getOutgoing(internalId2)));

        EdgeIterator iter = graph.getOutgoing(internalIdMain);
        assertTrue(iter.next());
        assertEquals(internalId1, iter.node());
        assertEquals(88643, iter.distance(), 1);
        assertTrue(iter.next());
        assertEquals(internalId2, iter.node());
        assertEquals(93146.888, iter.distance(), 1);
        assertTrue(iter.next());
        assertEquals(internalId4, iter.node());
        AbstractGraphTester.assertPList(Helper.createPointList(), iter.pillarNodes());

        // get third added location => 2
        iter = graph.getOutgoing(internalId2);
        assertTrue(iter.next());
        assertEquals(internalIdMain, iter.node());
        assertEquals(93146.888, iter.distance(), 1);
        assertFalse(iter.next());
    }

    @Test public void testOneWay() {
        OSMReader reader = preProcess(init(new OSMReader(createGraph(dir), 1000)));
        reader.writeOsm2Graph(getClass().getResourceAsStream("test-osm2.xml"));
        reader.flush();
        Graph graph = reader.getGraph();

        int internalIdMain = AbstractGraphTester.getIdOf(graph, 52);
        int internalId1 = AbstractGraphTester.getIdOf(graph, 51.2492152);
        int internalId2 = AbstractGraphTester.getIdOf(graph, 51.2);
        assertEquals(1, GraphUtility.count(graph.getOutgoing(internalId1)));
        assertEquals(1, GraphUtility.count(graph.getOutgoing(internalIdMain)));
        assertEquals(0, GraphUtility.count(graph.getOutgoing(internalId2)));

        EdgeIterator iter = graph.getOutgoing(internalIdMain);
        assertTrue(iter.next());
        assertEquals(internalId2, iter.node());

        iter = graph.getEdges(internalIdMain);
        assertTrue(iter.next());
        assertEquals(internalId1, iter.node());
        CarStreetType flags = new CarStreetType(iter.flags());
        assertTrue(flags.isMotorway());
        assertFalse(flags.isForward());
        assertTrue(flags.isBackward());

        assertTrue(iter.next());
        flags = new CarStreetType(iter.flags());
        assertTrue(flags.isMotorway());
        assertTrue(flags.isForward());
        assertFalse(flags.isBackward());
    }
}
