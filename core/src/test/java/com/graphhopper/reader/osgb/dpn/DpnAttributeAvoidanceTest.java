package com.graphhopper.reader.osgb.dpn;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.GHUtility;

public class DpnAttributeAvoidanceTest extends AbstractOsDpnReaderTest {
	
	@Test
	public void testAvoidSand() throws IOException {
		GraphHopperStorage graph = configureStorage(false, false);
		File file = new File("./src/test/resources/com/graphhopper/reader/osgb/dpn/os-dpn-avoid.xml");
		readGraphFile(graph, file);
		GHUtility.printInfo(graph, 0, 30, EdgeFilter.ALL_EDGES);
	}

}
