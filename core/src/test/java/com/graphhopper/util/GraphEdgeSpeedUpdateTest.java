package com.graphhopper.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.datexupdates.LatLongMetaData;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.GraphEdgeUpdate;

public class GraphEdgeSpeedUpdateTest {
	private static final String TEST_OSM_XML = "src/test/resources/com/graphhopper/reader/test-osm.xml";
	private static String defaultGraphLoc = "./target/graphstorage/default";
	private static GraphHopper closableInstance;

	protected EncodingManager encodingManager = new EncodingManager("CAR,FOOT");
	protected int defaultSize = 100;
	
	@BeforeClass
	public static void createGraph() {
		closableInstance = new GraphHopper().setStoreOnFlush(true).
	                setEncodingManager(new EncodingManager("CAR")).
	                setGraphHopperLocation(defaultGraphLoc).
	                setOSMFile(TEST_OSM_XML);
	        closableInstance.importOrLoad();
	}
	
	@AfterClass
	public static void closeGraph() {
		closableInstance.close();
		closableInstance.clean();
	}
	

	@Test
	public void testEdgeSpeedUpdate() {
		String lat = "51.2492150";
		String lon = "9.4317160";
		FlagEncoder encoder = closableInstance.getEncodingManager().getEncoder("CAR");
		
		QueryResult findClosest = closableInstance.getLocationIndex().findClosest(Double.parseDouble(lat), Double.parseDouble(lon), EdgeFilter.ALL_EDGES);
		long flags = findClosest.getClosestEdge().getFlags();
		assertNotEquals(20, encoder.getSpeed(flags));
		LatLongMetaData latLonMetadata = new LatLongMetaData("20", lat, lon);
		GraphEdgeUpdate.updateEdge(closableInstance, latLonMetadata);
		findClosest = closableInstance.getLocationIndex().findClosest(Double.parseDouble(lat), Double.parseDouble(lon), EdgeFilter.ALL_EDGES);
		flags = findClosest.getClosestEdge().getFlags();
		assertEquals(20, encoder.getSpeed(flags), 0);
	}
}
