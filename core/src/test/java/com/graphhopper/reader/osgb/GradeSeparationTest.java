package com.graphhopper.reader.osgb;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;

public class GradeSeparationTest extends AbstractOsItnReaderTest {

    private GraphHopperStorage graph;

    @Before
    public void setupGraph() {
        final boolean turnRestrictionsImport = true;
        final boolean is3D = false;
        graph = configureStorage(turnRestrictionsImport, is3D, true);
    }

    @Test
    public void testSimpleBridge() throws IOException {

        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-simple-bridge.xml");
        readGraphFile(graph, file);
        assertEquals(10, graph.getNodes());
        final EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        printNodes(explorer, 10);
        assertEquals(2, count(explorer.setBaseNode(0)));
        assertEquals(2, count(explorer.setBaseNode(1)));
        assertEquals(2, count(explorer.setBaseNode(2)));
        assertEquals(1, count(explorer.setBaseNode(3)));
        assertEquals(2, count(explorer.setBaseNode(4)));
        assertEquals(1, count(explorer.setBaseNode(5)));
        assertEquals(2, count(explorer.setBaseNode(6)));
        assertEquals(1, count(explorer.setBaseNode(7)));
        assertEquals(2, count(explorer.setBaseNode(8)));
        assertEquals(1, count(explorer.setBaseNode(9)));
    }

    @Test
    public void testBridgeWithRestrictedAccessOver() throws IOException {
        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-bridge-restricted-access-over.xml");
        readGraphFile(graph, file);
        final EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        printNodes(explorer, 10);
        assertEquals(5, graph.getNodes());
        assertEquals(2, count(explorer.setBaseNode(0)));
        assertEquals(2, count(explorer.setBaseNode(1)));
        assertEquals(1, count(explorer.setBaseNode(2)));
        assertEquals(2, count(explorer.setBaseNode(3)));
        assertEquals(1, count(explorer.setBaseNode(4)));
    }
    @Test
    public void testBridgeWithRestrictedAccessUnder() throws IOException {
        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-bridge-restricted-access-under.xml");
        readGraphFile(graph, file);
        assertEquals(5, graph.getNodes());
        final EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        printNodes(explorer, 5);
        assertEquals(2, count(explorer.setBaseNode(0)));
        assertEquals(2, count(explorer.setBaseNode(1)));
        assertEquals(1, count(explorer.setBaseNode(2)));
        assertEquals(2, count(explorer.setBaseNode(3)));
        assertEquals(1, count(explorer.setBaseNode(4)));

    }
    @Test
    public void testGradeSeparatedCentralNode() throws IOException {
        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-grade-separated-node.xml");
        readGraphFile(graph, file);
        final EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        printNodes(explorer, 5);
        assertEquals(5, graph.getNodes());
        assertEquals(2, count(explorer.setBaseNode(0)));
        assertEquals(2, count(explorer.setBaseNode(1)));
        assertEquals(1, count(explorer.setBaseNode(2)));
        assertEquals(2, count(explorer.setBaseNode(3)));
        assertEquals(1, count(explorer.setBaseNode(4)));
    }
}
