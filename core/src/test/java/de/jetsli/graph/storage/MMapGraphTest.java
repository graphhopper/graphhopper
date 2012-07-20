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

import de.jetsli.graph.util.EdgeIdIterator;
import de.jetsli.graph.util.Helper;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Test;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import static org.junit.Assert.*;
import static de.jetsli.graph.util.GraphUtility.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class MMapGraphTest extends AbstractGraphTester {

    protected String dir = "/tmp/GraphTest";
    protected SaveableGraph g;

    @After
    public void tearDown() throws IOException {
        if (g != null)
            g.close();
        g = null;
        Helper.deleteDir(new File(dir));
    }

    @Override
    Graph createGraph(int size) {
        return g = new MMapGraph(dir, size).createNew();
    }

    @Test
    public void testStats() {
        super.testDozendEdges();
        ((MMapGraph) g).stats(false);
    }

    @Test
    public void testNoDuplicateEdges() {
        Graph graph = createGraph(10);
        graph.edge(2, 1, 12, true);
        graph.edge(2, 3, 12, true);
        graph.edge(2, 3, 12, true);
        assertEquals(2, count(graph.getOutgoing(2)));

        graph.edge(3, 2, 12, true);
        assertEquals(2, count(graph.getOutgoing(2)));
    }

    // assume the following behaviour which allows the graph to stored bidirections more efficient
    @Test public void testOverwriteWillResultInSymetricUpdateOfEdgeWeight() {
        Graph g = createGraph(3);

        g.edge(1, 2, 12, true);
        EdgeIdIterator iter = g.getOutgoing(2);
        assertTrue(iter.next());
        assertEquals(12, iter.distance(), 1e-7);
        iter = g.getOutgoing(1);
        assertTrue(iter.next());
        assertEquals(12, iter.distance(), 1e-7);
        g.edge(1, 2, 11, false);
        iter = g.getOutgoing(2);
        assertTrue(iter.next());
        assertEquals(1, iter.nodeId());
        assertEquals(11, iter.distance(), 1e-7);
        iter = g.getOutgoing(1);
        assertTrue(iter.next());
        assertEquals(2, iter.nodeId());
        assertEquals(11, iter.distance(), 1e-7);
        g.edge(1, 2, 13, true);
        iter = g.getOutgoing(2);
        assertTrue(iter.next());
        assertEquals(1, iter.nodeId());
        assertEquals(13, iter.distance(), 1e-7);
        iter = g.getOutgoing(1);
        assertTrue(iter.next());
        assertEquals(2, iter.nodeId());
        assertEquals(13, iter.distance(), 1e-7);
    }

    @Test
    public void testIncreaseSize() throws IOException {
        MMapGraph graph = (MMapGraph) createGraph(10);
        for (int i = 0; i < 10; i++) {
            graph.setNode(i, 1, i);
        }

        graph.ensureCapacity(20);
        assertEquals(26, graph.getNodesCapacity());

        for (int i = 10; i < 20; i++) {
            graph.setNode(i, 1, i);
        }

        for (int i = 0; i < 20; i++) {
            assertEquals(i, graph.getLongitude(i), 1e-4);
        }

        for (int i = 20; i < 26; i++) {
            graph.setNode(i, 2, 2);
        }
        assertEquals(33, graph.getNodesCapacity());
    }

    @Test
    public void testMapped() throws Exception {
        assertTrue(ByteBuffer.allocateDirect(12) instanceof MappedByteBuffer);
        assertFalse(MMapGraph.isFileMapped(ByteBuffer.allocateDirect(12)));
        FileChannel fc = new RandomAccessFile(File.createTempFile("mmap", "test"), "rw").getChannel();
        ByteBuffer bb = fc.map(FileChannel.MapMode.READ_WRITE, 0, 10);
        assertTrue(MMapGraph.isFileMapped(bb));
        fc.close();
    }

    @Test
    public void testCalcEdgeSize() {
        MMapGraph mm = new MMapGraph(1);
        assertTrue(mm.calculateEdges(100) > 10);
        assertTrue(mm.calculateEdges(100) < 30);
        mm = new MMapGraph(1) {
            {
                try {
                    ensureEdgesCapacity(10);
                } catch (Exception ex) {
                }
                getNextFreeEdgeBlock();
                getNextFreeEdgeBlock();
                getNextFreeEdgeBlock();
            }
        };
        assertEquals(5, mm.calculateEdges(1));
    }

    @Test
    public void testSaveOnFlushOnly() throws IOException {
        String tmpDir = dir + "/test-persist-graph";
        MMapGraph mmgraph = new MMapGraph(tmpDir, 3).createNew(true);
        assertFalse(MMapGraph.isFileMapped(mmgraph.getEdges()));
        mmgraph.setNode(0, 10, 10);
        mmgraph.setNode(1, 11, 20);
        mmgraph.setNode(2, 12, 12);

        mmgraph.edge(0, 2, 200, true);
        mmgraph.edge(1, 2, 120, false);
        mmgraph.edge(0, 1, 100, true);

        checkGraph(mmgraph);
        mmgraph.close();

        mmgraph = new MMapGraph(tmpDir, 3);
        assertTrue(mmgraph.loadExisting());
        checkGraph(mmgraph);
    }

    @Test
    public void testSave() throws IOException {
        String tmpDir = dir + "/test-persist-graph";
        MMapGraph mmgraph = new MMapGraph(tmpDir, 3).createNew();
        assertTrue(MMapGraph.isFileMapped(mmgraph.getEdges()));
        mmgraph.setNode(0, 10, 10);
        mmgraph.setNode(1, 11, 20);
        mmgraph.setNode(2, 12, 12);

        mmgraph.edge(0, 1, 100, true);
        mmgraph.edge(0, 2, 200, true);
        mmgraph.edge(1, 2, 120, false);

        checkGraph(mmgraph);
        mmgraph.close();

        mmgraph = new MMapGraph(tmpDir, 1000);
        assertTrue(mmgraph.loadExisting());
        assertEquals(13, mmgraph.getNodesCapacity());
        checkGraph(mmgraph);
    }

    protected void checkGraph(Graph g) {
        assertEquals(3, g.getNodes());
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
