package com.graphhopper.reader.osgb;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;

import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.ShortestWeighting;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.RoundaboutInstruction;
import com.graphhopper.util.Translation;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.TranslationMapTest;

public class RoundaboutTest extends AbstractOsItnReaderTest {
	private final TranslationMap trMap = TranslationMapTest.SINGLETON;
	private final Translation tr = trMap.getWithFallBack(Locale.US);

	/**
	 * Alleys are not supported routes. This test is a simple (node A) - alley -
	 * (node B) - A Road - (node C) network. This means the alley should not be
	 * traversible and only nodes B and C should be present.
	 * 
	 * @throws IOException
	 */
	@Test
	public void testRoundabout() throws IOException {
		final boolean turnRestrictionsImport = true;
		final boolean is3D = false;
		final GraphHopperStorage graph = configureStorage(
				turnRestrictionsImport, is3D);

		final File file = new File(
				"./src/test/resources/com/graphhopper/reader/osgb/os-itn-roundabout-exit.xml");
		readGraphFile(graph, file);

		Path p = new Dijkstra(graph, carEncoder, new ShortestWeighting(),
				TraversalMode.NODE_BASED).calcPath(1, 8);
		InstructionList wayList = p.calcInstructions(tr);
		// Test instructions
		List<String> tmpList = pick("text", wayList.createJson());
		assertEquals(Arrays.asList("Continue onto PARIS STREET (B3183)",
				"At roundabout, take exit 1 onto CHEEKE STREET", "Finish!"), tmpList);

		// case of continuing a street through a roundabout
		p = new Dijkstra(graph, carEncoder, new ShortestWeighting(),
				TraversalMode.NODE_BASED).calcPath(1, 9);
		wayList = p.calcInstructions(tr);
		tmpList = pick("text", wayList.createJson());
		assertEquals(Arrays.asList("Continue onto PARIS STREET (B3183)",
				"At roundabout, take exit 3 onto HEAVITREE ROAD (B3183)", "Finish!"),
				tmpList);
	}
	
	List<String> pick( String key, List<Map<String, Object>> instructionJson )
    {
        List<String> list = new ArrayList<String>();

        for (Map<String, Object> json : instructionJson)
        {
            list.add(json.get(key).toString());
        }
        return list;
    }

}
