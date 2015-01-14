package com.graphhopper.reader.osgb;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;

public class ClassificationTest extends AbstractOsItnReaderTest {

    private GraphHopperStorage graph;

    @Before
    public void setupGraph() {
        final boolean turnRestrictionsImport = true;
        final boolean is3D = false;
        graph = configureStorage(turnRestrictionsImport, is3D, true);
    }

    @Test
    public void testFord() throws IOException {
        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-classification-ford.xml");
        readGraphFile(graph, file);
        final EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        printNodes(explorer, 3);
        assertEquals(3, graph.getNodes());
        assertEquals(1, count(explorer.setBaseNode(0)));
        assertEquals(2, count(explorer.setBaseNode(1)));
        assertEquals(1, count(explorer.setBaseNode(2)));
    }

    @Test
    public void testGate() throws IOException {
        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-classification-gate.xml");
        readGraphFile(graph, file);
        final EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        printNodes(explorer, 6);
        assertEquals(3, graph.getNodes());
        assertEquals(1, count(explorer.setBaseNode(0)));
        assertEquals(2, count(explorer.setBaseNode(1)));
        assertEquals(1, count(explorer.setBaseNode(2)));
    }
}
