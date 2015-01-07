package com.graphhopper.reader.osgb;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;

public class AlleyTest extends AbstractOsItnReaderTest {

    /**
     * Alleys are not supported routes. This test is a simple (node A) - alley - (node B) - A Road - (node C) network.
     * This means the alley should not be traversible and only nodes B and C should be present.
     * @throws IOException
     */
    @Test
    public void testAlley() throws IOException {
        final boolean turnRestrictionsImport = true;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-alley.xml");
        readGraphFile(graph, file);
        final EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        printNodes(explorer, 5);
        assertEquals(2, graph.getNodes());
        assertEquals(1, count(explorer.setBaseNode(0)));
        assertEquals(1, count(explorer.setBaseNode(1)));
    }

}
