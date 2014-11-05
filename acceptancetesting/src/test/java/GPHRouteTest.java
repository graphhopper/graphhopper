import static org.junit.Assert.assertTrue;

import java.util.HashSet;

import org.alternativevision.gpx.beans.Route;
import org.alternativevision.gpx.beans.Waypoint;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.ordnancesurvey.gpx.extensions.ExtensionConstants;
import uk.co.ordnancesurvey.gpx.graphhopper.GraphHopperGPXParserRouteTest;

public class GPHRouteTest {

	private static final Logger LOG = LoggerFactory
			.getLogger(GPHRouteTest.class);
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}
	
	@Ignore
	public void testrouteContainsTurn() {
		String path = getClass().getResource("sampleGraphHopper.gpx").getPath();
		GraphHopperGPXParserRouteTest ghrt = GraphHopperGPXParserRouteTest.getParserForgpxFileName(path);
		HashSet<Route> hs = ghrt.getRoutes();
		String turn = "turn sharp right onto Bellemoor Road";
		
		assertTrue(ghrt.routeContainsTurn(turn, hs.iterator().next()));
	}

	@Ignore
	public void testWayPointIsOnRoute() {
		
		String path = getClass().getResource("sampleGraphHopper.gpx").getPath();
		GraphHopperGPXParserRouteTest ghrt = GraphHopperGPXParserRouteTest.getParserForgpxFileName(path);
		Waypoint wayPoint = getTestWayPoint();
		HashSet<Route> hs = ghrt.getRoutes();
		
		assertTrue(ghrt.isWayPointOnRoute(wayPoint,hs.iterator().next()));
	}
	
	@Ignore
	public void testTotalRouteTime() {
		String path = getClass().getResource("sampleGraphHopper.gpx").getPath();
		GraphHopperGPXParserRouteTest ghrt = GraphHopperGPXParserRouteTest.getParserForgpxFileName(path);
		ghrt.getTotalRouteTime();
		assertTrue(true);
	}
	
	@Test
	public void testGetRouteAsGPX() {
		LOG.info("Starting To Test");
		GraphHopperGPXParserRouteTest ghrt = new GraphHopperGPXParserRouteTest();
		String gpxString = ghrt.parseRoute("50.93602556772844,-1.4194250106811523,50.92544987854478,-1.389212608337402", "gpx", "car");
		
		LOG.info(gpxString);
		HashSet<Route> hs = ghrt.getRoutes();
		
		assertTrue(ghrt.routeContainsTurn("turn sharp right onto ROCKLEIGH ROAD",hs.iterator().next()));
	}

	private Waypoint getTestWayPoint() {
		
		Waypoint wp = new Waypoint();
		wp.setLatitude(new Double(50.927146));
		wp.setLongitude(new Double(-1.416787));
		wp.addExtensionData(ExtensionConstants.AZIMUTH, "339");
		wp.addExtensionData(ExtensionConstants.DIRECTION, "N");
		wp.addExtensionData(ExtensionConstants.TIME, "2515");
		wp.addExtensionData(ExtensionConstants.DISTANCE, "13.974");
		
		return wp;
	}
}
