package com.graphhopper.reader.osgb.hn;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.osgb.AbstractOsItnReaderTest;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;

/**
 * Unit testing for the Highways Network data.
 * @author phopkins
 *
 */
public class OsHnReaderTest extends AbstractOsItnReaderTest {

    @Test
    @Ignore
    public void testReader() {
        String graphLoc = "./target/output/hn-gh";
        String inputFile = "/data/Development/highways_network";
        GraphHopper graphHopper = new GraphHopper(){
            @Override
            protected void postProcessing()
            {
                System.out.println("DON'T DO postProcessing()");
            }
            @Override
            protected void flush()
            {
                //                fullyLoaded = true;
            }

        }.setInMemory().setOSMFile(inputFile).setGraphHopperLocation(graphLoc).setCHEnable(false).setEncodingManager(encodingManager).setAsHnReader();
        // THIS WILL FAIL FOR NOW UNTIL THE READER GENERATES SOME OSM NODES
        graphHopper.importOrLoad();
        GraphStorage graph = graphHopper.getGraph();

    }

    /**
     * This test reads in the ITN data set WITHOUT any Highways Network data and asserts that the route from node ###79 to ###85  is along the shorter route (takes the TOP ROAD).
     */
    @Test
    public void testMotorwayARoadNetwork_NoHighwaysNetworkData() {
        String graphLoc = "./target/output/testMotorwayARoadNetwork_NoHighwaysNetworkData/os-itn-hn-test-network-gh";
        String inputFile = "./src/test/resources/com/graphhopper/reader/os-itn-hn-test-network.xml";
        GraphHopper graphHopper = new GraphHopper().setInMemory().setOSMFile(inputFile).setGraphHopperLocation(graphLoc).setCHEnable(false).setEncodingManager(encodingManager).setAsItnReader();
        graphHopper.importOrLoad();
        GraphStorage graph = graphHopper.getGraph();
        printNodes(graph.createEdgeExplorer(carOutEdges), 6);
        testNodes(graph);
        // 79 => 295000.000 90000.000 = -3.49   50.70
        // 80 => 290000.000,90000.000 = -3.56   50.70
        // 81 => 280000.000 90000.000 = -3.70   50.70
        // 82 => 270000.000,90000.000 = -3.84   50.69
        // 82 => 280000.000 80000.000 = -3.70   50.61
        // 85 => 265000.000 90000.000 = -3.91   50.69
        InstructionList instructionList = route(graphHopper, 50.70, -3.49, 50.69, -3.91);
        outputInstructionList(instructionList);

        assertEquals(Instruction.CONTINUE_ON_STREET, instructionList.get(0).getSign());
        assertEquals("START ROAD", instructionList.get(0).getName());
        assertEquals(Instruction.TURN_SLIGHT_RIGHT, instructionList.get(1).getSign());
        assertEquals("TOP ROAD", instructionList.get(1).getName());
        assertEquals(Instruction.TURN_SLIGHT_RIGHT, instructionList.get(2).getSign());
        assertEquals("END ROAD", instructionList.get(2).getName());
        assertEquals(Instruction.FINISH, instructionList.get(3).getSign());

    }

    /**
     * This test reads in the ITN data and decorates it with the Highways Network data.
     * It then asserts that the route from node ###79 to ###85 is along the longer route (takes the BOTTOM ROAD).
     * This is because TOP ROAD is specified in the Highways Network data as environment Urban and in ITN it is Single Carriageway
     * (BOTTOM ROAD is Dual Carriageway Motorway and therefore 70mph). Our rules
     * state that a non-Urban Single Carriageway is 60mph but an Urban Single Carriageway is 30mpg. Therefore the longer route is quicker
     */
    @Test
    public void testMotorwayARoadNetwork_WithHighwaysNetworkData() {
        String graphLoc = "./target/output/testMotorwayARoadNetwork_WithHighwaysNetworkData/os-itn-hn-test-network-gh";
        String inputFile = "./src/test/resources/com/graphhopper/reader/os-itn-hn-test-network.xml";


        Map<String, String> args = new HashMap<>();
        args.put("hn.data", "./src/test/resources/com/graphhopper/reader/hn/os-hn-single-urban.xml");
        args.put("hn.graph.location", "./target/output/testMotorwayARoadNetwork_WithHighwaysNetworkData/highways_network");
        args.put("graph.location", graphLoc);
        args.put("osmreader.osm", inputFile);
        args.put("config", "../config.properties");
        CmdArgs commandLineArguments = new CmdArgs(args);
        commandLineArguments = CmdArgs.readFromConfigAndMerge(commandLineArguments, "config", "graphhopper.config");

        GraphHopper graphHopper = new GraphHopper().setInMemory().setAsItnReader().init(commandLineArguments).setCHEnable(false).setEncodingManager(encodingManager);
        graphHopper.importOrLoad();
        GraphStorage graph = graphHopper.getGraph();

        printNodes(graph.createEdgeExplorer(carOutEdges), 6);
        testNodes(graph);
        // 79 => 295000.000 90000.000 = -3.49   50.70
        // 80 => 290000.000,90000.000 = -3.56   50.70
        // 81 => 280000.000 90000.000 = -3.70   50.70
        // 82 => 270000.000,90000.000 = -3.84   50.69
        // 82 => 280000.000 80000.000 = -3.70   50.61
        // 85 => 265000.000 90000.000 = -3.91   50.69
        InstructionList instructionList = route(graphHopper, 50.70, -3.49, 50.69, -3.91);
        outputInstructionList(instructionList);

        assertEquals(Instruction.CONTINUE_ON_STREET, instructionList.get(0).getSign());
        assertEquals("START ROAD", instructionList.get(0).getName());
        assertEquals(Instruction.TURN_SLIGHT_LEFT, instructionList.get(1).getSign());
        assertEquals("BOTTOM ROAD", instructionList.get(1).getName());
        assertEquals(Instruction.TURN_SLIGHT_LEFT, instructionList.get(2).getSign());
        assertEquals("END ROAD", instructionList.get(2).getName());
        assertEquals(Instruction.FINISH, instructionList.get(3).getSign());

    }

    private void testNodes(GraphStorage graph) {
        testNodes(graph, graph.createEdgeExplorer(carOutEdges));
    }
    private void testNodes(GraphStorage graph, EdgeExplorer explorer) {
        assertEquals(6, graph.getNodes());
        assertEquals(3, count(explorer.setBaseNode(0)));
        assertEquals(3, count(explorer.setBaseNode(1)));
        assertEquals(2, count(explorer.setBaseNode(2)));
        assertEquals(2, count(explorer.setBaseNode(3)));
        assertEquals(1, count(explorer.setBaseNode(4)));
        assertEquals(1, count(explorer.setBaseNode(5)));

    }

}
