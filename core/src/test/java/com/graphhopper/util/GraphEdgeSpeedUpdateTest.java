package com.graphhopper.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Locale;

import org.junit.After;
import org.junit.Test;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.reader.datexupdates.LatLongMetaData;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.shapes.GHPoint;

public class GraphEdgeSpeedUpdateTest {
	private static final String TEST_OSM_XML = "src/test/resources/com/graphhopper/reader/test-osm.xml";
	private static final String TEST_OSM_ROUTE_XML = "src/test/resources/com/graphhopper/reader/test-osm-updates.xml";
	private static String defaultGraphLoc = "./target/graphstorage/default";
	private GraphHopper closableInstance;

	protected EncodingManager encodingManager = new EncodingManager("CAR,FOOT");
	protected int defaultSize = 100;
	
	public void createGraph(String graphFile) {
		closableInstance = new GraphHopper().setStoreOnFlush(true).
	                setEncodingManager(new EncodingManager("CAR")).
	                setGraphHopperLocation(defaultGraphLoc).
	                setOSMFile(graphFile);
	        closableInstance.importOrLoad();
	}
	
	@After
	public void closeGraph() {
		closableInstance.close();
		closableInstance.clean();
	}
	

	@Test
	public void testEdgeSpeedUpdate() {
		String lat = "51.2492150";
		String lon = "9.4317160";
		createGraph(TEST_OSM_XML);
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
	
	@Test
	public void testEdgeSpeedUpdateEffectsRouting() {
		String lat = "11.2999";
		String lon = "50.999";
		double nodeSlat = 11.0;
		double nodeSlon = 49;
		double nodeFlat = 11.2;
		double nodeFlon = 53;
		
		createGraph(TEST_OSM_ROUTE_XML);
		FlagEncoder encoder = closableInstance.getEncodingManager().getEncoder("CAR");
		
		GHPoint startPlace = new GHPoint(nodeSlat, nodeSlon);
		GHPoint endPlace = new GHPoint(nodeFlat, nodeFlon);
		GHRequest request = new GHRequest(startPlace, endPlace );
		request.setWeighting("fastest");
		request.setVehicle(EncodingManager.CAR);
		GHResponse routeOne = closableInstance.route(request);
		PointList pointsOriginalRoute = routeOne.getPoints();
		
		GHPoint dRoutePoint = new GHPoint(11.299999869614837,51.0);
		GHPoint bRoutePoint = new GHPoint(12,51.0);
		assertEquals("Center Point of Route Should have been Node D", dRoutePoint, pointsOriginalRoute.toGHPoint(2));
		QueryResult findClosest = closableInstance.getLocationIndex().findClosest(Double.parseDouble(lat), Double.parseDouble(lon), EdgeFilter.ALL_EDGES);
		EdgeIteratorState closestEdge = findClosest.getClosestEdge();
		assertEquals("A D C", closestEdge.getName());
		long flags = closestEdge.getFlags();
		assertEquals(100, encoder.getSpeed(flags), 0);
		LatLongMetaData latLonMetadata = new LatLongMetaData("20", lat, lon);
		GraphEdgeUpdate.updateEdge(closableInstance, latLonMetadata);
		
		GHResponse routeTwo = closableInstance.route(request);
		PointList pointsUpdateRoute = routeTwo.getPoints();
		assertNotEquals("Center Point of Route Should no longer have been Node D", dRoutePoint, pointsUpdateRoute.toGHPoint(2));
		assertEquals("Center Point of Route Should now be Node B", bRoutePoint, pointsUpdateRoute.toGHPoint(2));
		checkRoutePointSame(0, pointsOriginalRoute, pointsUpdateRoute);
		checkRoutePointSame(1, pointsOriginalRoute, pointsUpdateRoute);
		checkRoutePointSame(3, pointsOriginalRoute, pointsUpdateRoute);
		checkRoutePointSame(4, pointsOriginalRoute, pointsUpdateRoute);
	}

	private void checkRoutePointSame(int i, PointList pointsOriginalRoute,
			PointList pointsUpdateRoute) {
		assertEquals(i + " Point of Route Should now be same on both routes", pointsOriginalRoute.toGHPoint(i), pointsUpdateRoute.toGHPoint(i));
		
	}
}
