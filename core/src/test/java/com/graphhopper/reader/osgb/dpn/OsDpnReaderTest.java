package com.graphhopper.reader.osgb.dpn;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.GHUtility;

public class OsDpnReaderTest extends AbstractOsDpnReaderTest {

    @Test
    public void testReadDpnSample() throws IOException {
        final boolean turnRestrictionsImport = true;
        final boolean is3D = false;
        final GraphHopperStorage graph = configureStorage(
                turnRestrictionsImport, is3D);

        final File file = new File(
                "./src/test/resources/com/graphhopper/reader/osgb/dpn/os-dpn-sample.xml");
        readGraphFile(graph, file);
        GHUtility.printInfo(graph, 0, 30, EdgeFilter.ALL_EDGES);
        carAllExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(
                carEncoder, true, true));
        printNodes(carAllExplorer, 30);
    }

}
