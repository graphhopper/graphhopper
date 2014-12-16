package com.graphhopper.reader.osgb;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

public class BusFlagEncoderTest extends AbstractOsItnReaderTest{

    protected EncodingManager createEncodingManager() {
        return new EncodingManager(busEncoder);
    }
    
    
    @Test
    public void testNoEntryExceptForBusesTrue() throws IOException {
        final boolean turnRestrictionsImport = true;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D);

        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-noentry-except-for-buses-true.xml");
        readGraphFile(graph, file);

        // ******************* START OF Print
        // ***************************************
        EdgeFilter busOutEdges = new DefaultEdgeFilter(busEncoder, false, true);
        
        final EdgeExplorer explorer = graph.createEdgeExplorer(busOutEdges);

        printNodes(explorer, 7);
        // ***********************************************************************

        checkNonNoEntryNetwork(graph, explorer);
    }
    
    private void checkNonNoEntryNetwork(GraphHopperStorage graph, EdgeExplorer explorer) {
        // Assert that our graph has 7 nodes
        assertEquals(5, graph.getNodes());

        // Assert that there are four links/roads/edges that can be seen from
        // the base node;
        assertEquals(4, count(explorer.setBaseNode(0)));

        EdgeFilter busOutEdges = new DefaultEdgeFilter(busEncoder, false, true);

        GHUtility.printInfo(graph, 0, 20, busOutEdges);
        // Assert that when the explorer is on node 1 it can travel one edges
        assertEquals(1, count(explorer.setBaseNode(1)));

        // Assert that when the explorer is positioned on base 2 it can only
        // travel one edge
        assertEquals(1, count(explorer.setBaseNode(2)));

        // Assert that when the explorer is positioned on node 3 it can
        // travel 1 edges
        assertEquals(1, count(explorer.setBaseNode(3)));

        // Assert that when the explorer is positioned on node 4 it can only
        // travel 1 edge
        assertEquals(1, count(explorer.setBaseNode(4)));

        EdgeExplorer busAllExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(busEncoder, true, true));
        // Starting at node zero I should be able to travel back and forth to
        // four nodes?
        EdgeIterator iter = busAllExplorer.setBaseNode(0);
        assertTrue(iter.next());
        evaluateRouting(iter, 4, true, true, false, busEncoder);
        evaluateRouting(iter, 3, true, true, false, busEncoder);
        evaluateRouting(iter, 2, true, true, false, busEncoder);
        evaluateRouting(iter, 1, true, true, true, busEncoder);

        // Starting at node 1
        iter = busAllExplorer.setBaseNode(1);
        assertTrue(iter.next());
        // I should be able to get to node 0 in a forward and backward direction
        // and have exhausted all the edges
        evaluateRouting(iter, 0, true, true, true, busEncoder);

        // Starting at node 2
        iter = busAllExplorer.setBaseNode(2);
        assertTrue(iter.next());
        // I should be able to travel back from node 3 but not to it
        // I should be able to travel to and from node 1 and have exhausted all
        // the edges
        evaluateRouting(iter, 0, true, true, true, busEncoder);

        // Starting at node 3
        iter = busAllExplorer.setBaseNode(3);
        assertTrue(iter.next());
        evaluateRouting(iter, 0, true, true, true, busEncoder);

        // Starting at node 4
        iter = busAllExplorer.setBaseNode(4);
        assertTrue(iter.next());
        evaluateRouting(iter, 0, true, true, true, busEncoder);
    }
}
