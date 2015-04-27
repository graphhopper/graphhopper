package com.graphhopper.reader.osgb.dpn;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.graphhopper.GraphHopper;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.InstructionList;

public class DpnAttributeAvoidanceTest extends AbstractOsDpnReaderTest {
	

	@Test
	public void testAvoidBoulder() throws IOException {
		File file = new File("./src/test/resources/com/graphhopper/reader/osgb/dpn/os-dpn-avoid.xml");
		GraphHopper hopper = new GraphHopper();
		Map<String, String> map = new HashMap<String, String>();
		map.put("graph.flagencoders", "foot");
		map.put("osmreader.osm", file.getAbsolutePath());
		map.put("reader.implementation", "OSDPN");
		map.put("prepare.chweighting", "none");
		map.put("graph.location", "./target/output/dpn-avoid-gh");
		CmdArgs args = new CmdArgs(map);
		hopper.init(args);
		hopper.importOrLoad();
		InstructionList instructionList = route(hopper, 50.70, -3.49, 50.69, -3.91, null);
        assertEquals("Should be Link 17 as that is the shorteste route", "Link 17", instructionList.get(1).getName());
        instructionList = route(hopper, 50.70, -3.49, 50.69, -3.91, "boulders");
        assertEquals("Should be Link 19 as that avoids the boulder field", "Link 19", instructionList.get(1).getName());
	}

}
