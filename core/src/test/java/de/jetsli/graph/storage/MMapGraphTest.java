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
package de.jetsli.graph.storage;

import de.jetsli.graph.util.Helper;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Test;
import static de.jetsli.graph.util.MyIteratorable.*;
import java.nio.MappedByteBuffer;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class MMapGraphTest extends AbstractGraphTester {

    private String dir = "/tmp/MMapGraphTest";
    protected MMapGraph g;

    public void deleteTestGraphs() {
        Helper.deleteDir(new File(dir));
        new File(dir).mkdirs();
    }

    @Override
    Graph createGraph(int size) {
        deleteTestGraphs();
        return g = new MMapGraph(dir + "/testgraph", size).createNew();
    }

    @After
    public void tearDown() throws IOException {
        if (g != null)
            g.close();
        g = null;
        Helper.deleteDir(new File(dir));
    }

    @Test
    public void testStats() {
        super.testDozendEdges();
        g.stats();
    }

    @Test
    public void testIncreaseSize() throws IOException {
        MMapGraph graph = (MMapGraph) createGraph(10);
        for (int i = 0; i < 10; i++) {
            graph.addLocation(1, i);
        }

        graph.ensureCapacity(20);
        assertEquals(26, graph.getNodesCapacity());

        for (int i = 10; i < 20; i++) {
            graph.addLocation(1, i);
        }

        for (int i = 0; i < 20; i++) {
            assertEquals(i, graph.getLongitude(i), 1e-4);
        }

        for (int i = 0; i < 6; i++) {
            graph.addLocation(2, 2);
        }
        assertEquals(33, graph.getNodesCapacity());
    }

    @Test
    public void testSaveOnFlushOnly() throws IOException {
        String file = dir + "/test-persist-graph";
        Helper.deleteDir(new File(dir));
        new File(dir).mkdirs();
        MMapGraph mmgraph = new MMapGraph(file, 10).createNew(true);
        assertFalse(mmgraph.getEdges() instanceof MappedByteBuffer);
        mmgraph.addLocation(10, 10);
        mmgraph.addLocation(11, 20);
        mmgraph.addLocation(12, 12);
                
        mmgraph.edge(0, 2, 200, true);
        mmgraph.edge(1, 2, 120, false);
        mmgraph.edge(0, 1, 100, true);

        checkGraph(mmgraph);        
        mmgraph.close();
        assertTrue(mmgraph.loadExisting());
        checkGraph(mmgraph);
    }

    @Test
    public void testSave() throws IOException {
        String file = dir + "/test-persist-graph";
        deleteTestGraphs();
        MMapGraph mmgraph = new MMapGraph(file, 3).createNew();
        assertTrue(mmgraph.getEdges() instanceof MappedByteBuffer);
        mmgraph.addLocation(10, 10);
        mmgraph.addLocation(11, 20);
        mmgraph.addLocation(12, 12);
        
        mmgraph.edge(0, 1, 100, true);
        mmgraph.edge(0, 2, 200, true);
        mmgraph.edge(1, 2, 120, false);        

        checkGraph(mmgraph);
        mmgraph.close();

        mmgraph = new MMapGraph(file, 1000);
        assertTrue(mmgraph.loadExisting());
        assertEquals(13, mmgraph.getNodesCapacity());
        checkGraph(mmgraph);
    }

    private void checkGraph(Graph g) {
        assertEquals(3, g.getLocations());
        assertEquals(10, g.getLatitude(0), 1e-2);
        assertEquals(10, g.getLongitude(0), 1e-2);
        assertEquals(2, count(g.getOutgoing(0)));
        assertTrue(contains(g.getOutgoing(0), 1, 2));

        assertEquals(11, g.getLatitude(1), 1e-2);
        assertEquals(20, g.getLongitude(1), 1e-2);
        assertEquals(2, count(g.getOutgoing(1)));
        assertTrue(contains(g.getOutgoing(1), 0, 2));

        assertEquals(12, g.getLatitude(2), 1e-2);
        assertEquals(12, g.getLongitude(2), 1e-2);
        assertEquals(1, count(g.getOutgoing(2)));
        assertTrue(contains(g.getOutgoing(2), 0));
    }
}
