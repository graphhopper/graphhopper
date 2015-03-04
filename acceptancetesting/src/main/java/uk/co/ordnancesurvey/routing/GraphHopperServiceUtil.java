package uk.co.ordnancesurvey.routing;

import static org.junit.Assert.assertTrue;

import java.util.HashSet;

import org.alternativevision.gpx.beans.Route;
import org.alternativevision.gpx.beans.Waypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.ordnancesurvey.gpx.extensions.ExtensionConstants;
import uk.co.ordnancesurvey.gpx.graphhopper.GraphHopperGPXParserRouteTest;

public class GraphHopperServiceUtil {

	private static final Logger LOG = LoggerFactory
			.getLogger(GraphHopperServiceUtil.class);

	public GraphHopperGPXParserRouteTest testGetRouteAsGPX() {
		LOG.info("Starting To Test");
		GraphHopperGPXParserRouteTest ghrt = new GraphHopperGPXParserRouteTest();
		String gpxString = ghrt
				.parseRoute(
						"50.93602556772844,-1.4194250106811523,50.92544987854478,-1.389212608337402",
						"gpx", "car");

		LOG.info(gpxString);
		HashSet<Route> hs = ghrt.getRoutes();

		assertTrue(ghrt.routeContainsTurn(
				"turn sharp right onto ROCKLEIGH ROAD", hs.iterator().next()));
		return ghrt;
	}
	
	

}
