/*
 *  Copyright 2012 Peter Karich info@jetsli.de
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
package de.jetsli.graph.reader;

import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.EdgeIdIterator;
import de.jetsli.graph.util.Helper;
import de.jetsli.graph.util.GraphUtility;
import java.io.File;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class OSMReaderTest {

    private String dir = "/tmp/OSMReaderTrialsTest/test-db";       
    private OSMReader reader;
    
    @Before
    public void setUp() {
        new File(dir).mkdirs();
    }

    @After
    public void tearDown() {
        Helper.deleteDir(new File(dir));
    }

    @Test public void testMain() {
        reader = new OSMReader(dir, 1000);
        reader.preprocessAcceptHighwaysOnly(getClass().getResourceAsStream("test1.xml"));
        reader.writeOsm2Graph(getClass().getResourceAsStream("test1.xml"));
        reader.flush();
        Graph graph = reader.getGraph();
        assertEquals(4, graph.getNodes());
        assertEquals(1, GraphUtility.count(graph.getOutgoing(0)));
        assertEquals(3, GraphUtility.count(graph.getOutgoing(1)));
        assertEquals(1, GraphUtility.count(graph.getOutgoing(2)));

        EdgeIdIterator iter = graph.getOutgoing(1);
        assertTrue(iter.next());
        assertEquals(0, iter.nodeId());
        assertEquals(88.643, iter.distance(), 1e-3);
        assertTrue(iter.next());
        assertEquals(2, iter.nodeId());
        assertEquals(93.146888, iter.distance(), 1e-3);

        // get third added location => 2
        iter = graph.getOutgoing(2);
        assertTrue(iter.next());
        assertEquals(1, iter.nodeId());
        assertEquals(93.146888, iter.distance(), 1e-3);
    }
    
    @Test public void testWithBounds() {
        reader = new OSMReader(dir, 1000) {

            @Override public boolean isInBounds(double lat, double lon) {
                return lat > 49 && lon > 8;
            }            
        };
        reader.preprocessAcceptHighwaysOnly(getClass().getResourceAsStream("test1.xml"));
        reader.writeOsm2Graph(getClass().getResourceAsStream("test1.xml"));
        reader.flush();
        Graph graph = reader.getGraph();
        assertEquals(3, graph.getNodes());
        assertEquals(1, GraphUtility.count(graph.getOutgoing(0)));
        assertEquals(2, GraphUtility.count(graph.getOutgoing(1)));
        assertEquals(1, GraphUtility.count(graph.getOutgoing(2)));

        EdgeIdIterator iter = graph.getOutgoing(1);
        assertTrue(iter.next());
        assertEquals(0, iter.nodeId());
        assertEquals(88.643, iter.distance(), 1e-3);
        assertTrue(iter.next());
        assertEquals(2, iter.nodeId());
        assertEquals(93.146888, iter.distance(), 1e-3);

        // get third added location => 2
        iter = graph.getOutgoing(2);
        assertTrue(iter.next());
        assertEquals(1, iter.nodeId());
        assertEquals(93.146888, iter.distance(), 1e-3);
    }
}
