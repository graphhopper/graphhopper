package com.graphhopper.reader.osgb.dpn;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;

import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.BikeFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.routing.util.OsFootFlagEncoder;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

public abstract class AbstractOsDpnReaderTest {

	protected EncodingManager encodingManager;
    protected EdgeFilter footOutEdges;
    protected EdgeFilter footInEdges;
    protected boolean turnCosts = false;
    protected BikeFlagEncoder bikeEncoder;
    protected FootFlagEncoder footEncoder;
	protected EdgeExplorer footOutExplorer;

    // RoadNode 880
    protected static double node0Lat = 50.6992070044d;
    protected static double node0Lon = -3.55893724720532d;

    // RoadNode 881
    protected static double node1Lat = 50.6972276414d;
    protected static double node1Lon = -3.70047108174d;

    // RoadNode 882
    protected static double node2Lat = 50.6950765311d;
    protected static double node2Lon = -3.84198830979d;

    // RoadNode 883
    protected static double node3Lat = 50.6522837438d;
    protected static double node3Lon = -3.69884731399d;

    // RoadNode 884
    protected static double node4Lat = 50.7421711523d;
    protected static double node4Lon = -3.70209900111d;

    @Before
    public void initEncoding() {
        if (turnCosts) {
            bikeEncoder = new BikeFlagEncoder(4, 2, 3);
        } else {
            bikeEncoder = new BikeFlagEncoder();
        }

        footEncoder = new OsFootFlagEncoder();
        encodingManager = createEncodingManager();
    }

    /**
     * So we can create a specific encoding manager in subclasses
     *
     * @return
     */
    protected EncodingManager createEncodingManager() {
    	List<FlagEncoder> list = new ArrayList<FlagEncoder>();
    	list.add(footEncoder);
    	list.add(bikeEncoder);
        return new EncodingManager(list, 8);
    }

    protected OsDpnReader readGraphFile(GraphHopperStorage graph, File file)
            throws IOException {
        OsDpnReader osItnReader = new OsDpnReader(graph);
        System.out.println("Read " + file.getAbsolutePath());
        osItnReader.setOSMFile(file);
        osItnReader.setEncodingManager(encodingManager);
        osItnReader.readGraph();
        return osItnReader;
    }

    protected GraphHopperStorage configureStorage(
            boolean turnRestrictionsImport, boolean is3D) {
        String directory = "/tmp";
        GraphExtension extendedStorage = turnRestrictionsImport ? new TurnCostExtension() : new GraphExtension.NoExtendedStorage();
        GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory(
                directory, false), encodingManager, is3D, extendedStorage);
        footOutExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(footEncoder, false, true));
        return graph;
    }

    protected int getEdge(int from, int to) {
        EdgeIterator iter = footOutExplorer.setBaseNode(from);
        while (iter.next()) {
            if (iter.getAdjNode() == to) {
                return iter.getEdge();
            }
        }
        return EdgeIterator.NO_EDGE;
    }

    protected void evaluateRouting(final EdgeIterator iter, final int node,
            final boolean forward, final boolean backward,
            final boolean finished) {
        evaluateRouting(iter, node, forward, backward, finished, footEncoder);
    }

    protected void evaluateRouting(final EdgeIterator iter, final int node,
            final boolean forward, final boolean backward,
            final boolean finished, AbstractFlagEncoder flagEncoder) {
        assertEquals("Incorrect adjacent node", node, iter.getAdjNode());
        assertEquals("Incorrect forward instructions", forward,
                flagEncoder.isForward(iter.getFlags()));
        assertEquals("Incorrect backward instructions", backward,
                flagEncoder.isBackward(iter.getFlags()));
        assertEquals(!finished, iter.next());
    }

    protected void printNodes(EdgeExplorer outExplorer, int numNodes) {
        for (int i = 0; i < numNodes; i++) {
            // logger.info("Node " + i + " " +
            // count(outExplorer.setBaseNode(i)));
            System.out.println("Node " + i + " "
                    + count(outExplorer.setBaseNode(i)));
        }

        EdgeIterator iter = null;
        for (int i = 0; i < numNodes; i++) {
            iter = outExplorer.setBaseNode(i);
            while (iter.next()) {
                // logger.info(i+" Adj node is " + iter.getAdjNode());
                System.out.println(i + " Adj node is " + iter.getAdjNode());
            }
        }
    }
}
