package com.graphhopper.reader.osgb;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;

public class WickhamDirectionErrorTest extends AbstractOsItnReaderTest {

    @Test
    public void testLeftTurn() throws IOException {
        final boolean turnRestrictionsImport = true;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(turnRestrictionsImport, is3D, true);

        final File file = new File("./src/test/resources/com/graphhopper/reader/os-itn-wickham-direction-error.xml");
        readGraphFile(graph, file);
        testNodes(graph, printNodes(graph));
    }

    @Test
    public void testActualGraph() {
        String graphLoc = "./target/output/os-itn-wickham-direction-error-gh";
        String inputFile = "./src/test/resources/com/graphhopper/reader/os-itn-wickham-direction-error.xml";
        GraphHopper graphHopper = new GraphHopper().setInMemory().setOSMFile(inputFile).setGraphHopperLocation(graphLoc).setCHEnable(false).setEncodingManager(encodingManager).setAsItnReader();
        graphHopper.importOrLoad();
        GraphStorage graph = graphHopper.getGraph();
        //        printNodes(graph);
        testNodes(graph);

        InstructionList instructionList = route(graphHopper, 50.899566,-1.183887, 50.899554,-1.183985);
        assertEquals(Instruction.CONTINUE_ON_STREET, instructionList.get(0).getSign());
        assertEquals(Instruction.TURN_SHARP_LEFT, instructionList.get(1).getSign());
        assertEquals(Instruction.FINISH, instructionList.get(2).getSign());

        instructionList = route(graphHopper, 50.899769,-1.184209, 50.899554,-1.183985);
        assertEquals(Instruction.CONTINUE_ON_STREET, instructionList.get(0).getSign());
        assertEquals(Instruction.TURN_RIGHT, instructionList.get(1).getSign());
        assertEquals(Instruction.FINISH, instructionList.get(2).getSign());

        instructionList = route(graphHopper, 50.899554,-1.183985, 50.899566,-1.183887);
        assertEquals(Instruction.CONTINUE_ON_STREET, instructionList.get(0).getSign());
        assertEquals(Instruction.TURN_SHARP_RIGHT, instructionList.get(1).getSign());
        assertEquals(Instruction.FINISH, instructionList.get(2).getSign());

        instructionList = route(graphHopper, 50.899554,-1.183985, 50.899769,-1.184209);
        assertEquals(Instruction.CONTINUE_ON_STREET, instructionList.get(0).getSign());
        assertEquals(Instruction.TURN_LEFT, instructionList.get(1).getSign());
        assertEquals(Instruction.FINISH, instructionList.get(2).getSign());

        instructionList = route(graphHopper, 50.899799,-1.183769, 50.899368,-1.184035);
        assertEquals(Instruction.CONTINUE_ON_STREET, instructionList.get(0).getSign());
        assertEquals(Instruction.TURN_SLIGHT_LEFT, instructionList.get(1).getSign());
        assertEquals(Instruction.FINISH, instructionList.get(2).getSign());

        instructionList = route(graphHopper, 50.899368,-1.184035, 50.899799,-1.183769);
        assertEquals(Instruction.CONTINUE_ON_STREET, instructionList.get(0).getSign());
        assertEquals(Instruction.TURN_SLIGHT_RIGHT, instructionList.get(1).getSign());
        assertEquals(Instruction.FINISH, instructionList.get(2).getSign());

    }

    @Test
    @Ignore
    public void testWorkingTurn() {
        String graphLoc = "/home/phopkins/Documents/graphhopper/core/itn-gh";
        String inputFile = "/home/phopkins/Development/OSMMITN/data";
        EncodingManager enc = new EncodingManager(new CarFlagEncoder(5, 5, 3));
        GraphHopper graphHopper = new GraphHopper().setInMemory().setOSMFile(inputFile).setGraphHopperLocation(graphLoc).setCHEnable(false).setEncodingManager(enc).setAsItnReader();
        graphHopper.importOrLoad();
        route(graphHopper, 50.901825,-1.18542, 50.90206,-1.185326);

    }

    private EdgeExplorer printNodes(GraphStorage graph) {
        final EdgeExplorer explorer = graph.createEdgeExplorer(carOutEdges);
        printNodes(explorer, 9);
        return explorer;
    }
    private void testNodes(GraphStorage graph) {
        testNodes(graph, graph.createEdgeExplorer(carOutEdges));
    }
    private void testNodes(GraphStorage graph, EdgeExplorer explorer) {
        assertEquals(9, graph.getNodes());
        assertEquals(4, count(explorer.setBaseNode(0)));
        assertEquals(2, count(explorer.setBaseNode(1)));
        assertEquals(1, count(explorer.setBaseNode(2)));
        assertEquals(2, count(explorer.setBaseNode(3)));
        assertEquals(1, count(explorer.setBaseNode(4)));
        assertEquals(2, count(explorer.setBaseNode(5)));
        assertEquals(1, count(explorer.setBaseNode(6)));
        assertEquals(2, count(explorer.setBaseNode(7)));
        assertEquals(1, count(explorer.setBaseNode(8)));

    }
}
