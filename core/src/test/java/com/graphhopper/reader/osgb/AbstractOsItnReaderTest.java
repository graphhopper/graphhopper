package com.graphhopper.reader.osgb;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import org.junit.Before;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.reader.osgb.itn.OsItnReader;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.BikeFlagEncoder;
import com.graphhopper.routing.util.BusFlagEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.Translation;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.shapes.GHPoint;

public abstract class AbstractOsItnReaderTest {

    protected EncodingManager encodingManager;// = new
    // EncodingManager("CAR");//"car:com.graphhopper.routing.util.RelationCarFlagEncoder");
    protected CarFlagEncoder carEncoder;// = (RelationCarFlagEncoder)
    protected BusFlagEncoder busEncoder;// = (RelationCarFlagEncoder)
    // encodingManager
    // .getEncoder("CAR");
    protected EdgeFilter carOutEdges;// = new DefaultEdgeFilter(
    // carEncoder, false, true);
    protected EdgeFilter carInEdges;
    protected boolean turnCosts = true;
    protected EdgeExplorer carOutExplorer;
    protected EdgeExplorer carAllExplorer;
    protected BikeFlagEncoder bikeEncoder;
    protected FootFlagEncoder footEncoder;

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
            carEncoder = new CarFlagEncoder(5, 5, 3);
            busEncoder = new BusFlagEncoder(5, 5, 3);
            // carEncoder = new RelationCarFlagEncoder(5, 5, 3);
            bikeEncoder = new BikeFlagEncoder(4, 2, 3);
        } else {
            carEncoder = new CarFlagEncoder();
            busEncoder = new BusFlagEncoder();
            // carEncoder = new RelationCarFlagEncoder();
            bikeEncoder = new BikeFlagEncoder();
        }

        footEncoder = new FootFlagEncoder();
        carOutEdges = new DefaultEdgeFilter(carEncoder, false, true);
        carInEdges = new DefaultEdgeFilter(carEncoder, true, false);
        // encodingManager = new EncodingManager(footEncoder, carEncoder,
        // bikeEncoder);
        encodingManager = createEncodingManager();
        // encodingManager = new EncodingManager("car|turnCosts=true");
    }

    /**
     * So we can create a specific encoding manager in subclasses
     *
     * @return
     */
    protected EncodingManager createEncodingManager() {
        return new EncodingManager(footEncoder, carEncoder, bikeEncoder);
    }

    protected OsItnReader readGraphFile(GraphHopperStorage graph, File file)
            throws IOException {
        OsItnReader osItnReader = new OsItnReader(graph);
        System.out.println("Read " + file.getAbsolutePath());
        osItnReader.setOSMFile(file);
        osItnReader.setEncodingManager(encodingManager);
        osItnReader.readGraph();
        return osItnReader;
    }
    protected GraphHopperStorage configureStorage(
            boolean turnRestrictionsImport, boolean is3D) {
        return configureStorage(turnRestrictionsImport, is3D, false);
    }
    protected GraphHopperStorage configureStorage(
            boolean turnRestrictionsImport, boolean is3D, boolean addAdditionalTowerNodes) {
        String directory = "/tmp";
        GraphExtension extendedStorage = turnRestrictionsImport ? new TurnCostExtension() : new GraphExtension.NoExtendedStorage();
        GraphHopperStorage graph = new GraphHopperStorage(new RAMDirectory(
                directory, false), encodingManager, is3D, extendedStorage);
        graph.getProperties().put("add.additional.tower.nodes", addAdditionalTowerNodes);
        return graph;
    }

    protected void checkSimpleNodeNetwork(GraphHopperStorage graph) {
        EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        printNodes(explorer, 9);
        assertEquals(4, count(explorer.setBaseNode(0)));
        assertEquals(1, count(explorer.setBaseNode(1)));
        assertEquals(1, count(explorer.setBaseNode(2)));
        assertEquals(1, count(explorer.setBaseNode(3)));
        assertEquals(1, count(explorer.setBaseNode(4)));

        EdgeIterator iter = explorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals("OTHER ROAD", iter.getName());
        iter.next();
        assertEquals("OTHER ROAD", iter.getName());
        iter.next();
        assertEquals("BONHAY ROAD", iter.getName());
        iter.next();
        assertEquals("BONHAY ROAD", iter.getName());
        assertFalse(iter.next());
    }

    protected int getEdge(int from, int to) {
        EdgeIterator iter = carOutExplorer.setBaseNode(from);
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
        evaluateRouting(iter, node, forward, backward, finished, carEncoder);
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

    protected InstructionList route(GraphHopper graphHopper, double lat1, double lon1, double lat2, double lon2) {
        GHPoint start = new GHPoint(lat1, lon1);
        GHPoint end = new GHPoint(lat2, lon2);
        System.out.println("Route from " + start + " to " + end);
        GHRequest ghRequest = new GHRequest(start, end);
        ghRequest.setVehicle("car");
        GHResponse ghResponse = graphHopper.route(ghRequest);
        //        System.err.println("ghResponse.getPoints() " + ghResponse.getPoints());
        InstructionList instructionList = ghResponse.getInstructions();
        //        outputInstructionList(instructionList);
        return instructionList;
    }
    protected void outputInstructionList(InstructionList instructionList) {
        //        System.err.println("ghResponse.getInstructions() " + ghResponse.getInstructions());
        //        System.err.println("ghResponse.getDebugInfo() " + ghResponse.getDebugInfo());
        System.out.println("Turn Descriptions:");
        Translation tr = new TranslationMap().doImport().getWithFallBack(Locale.US);
        for (Instruction instruction : instructionList) {
            System.out.println("\t" + instruction.getName() + "\t" + instruction.getDistance() + "\t" + instruction.getSign() + "\t" + instruction.getTime() + "\t" + instruction.getTurnDescription(tr));
        }
        System.out.println("End Turn Descriptions");

    }

}
