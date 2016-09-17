package com.graphhopper;

import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.util.Helper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GraphHopperGtfsIT {

	private static final String graphFileFoot = "target/graphhopperIT-foot";
	private GraphHopperGtfs graphHopper;

	@Before
	public void init() {
		Helper.removeDir(new File(graphFileFoot));

		graphHopper = new GraphHopperGtfs();
		graphHopper.setCHEnabled(false);
		graphHopper.setGtfsFile("files/sample-feed.zip");
		graphHopper.setGraphHopperLocation(graphFileFoot);
		graphHopper.importOrLoad();
	}

	@Test
	public void testRoute1() {
		final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
		final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
		assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON, (6 * 60 * 60) + (49 * 60));
	}

	@Test
	public void testRoute2() {
		final double FROM_LAT = 36.914894, FROM_LON = -116.76821; // NADAV stop
		final double TO_LAT = 36.909489, TO_LON = -116.768242; // DADAN stop
		assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON, (6 * 60 * 60) + (19 * 60));
	}

	@Test
	public void testRoute3() {
		final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
		final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop
		assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON, (6 * 60 * 60) + (5 * 60));
	}

	@Test
	public void testRoute4() {
		final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
		final double TO_LAT = 36.914894, TO_LON = -116.76821; // NADAV stop
		assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON, (6 * 60 * 60) + (12 * 60));
	}

	@Test
	public void testRoute5() {
		final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
		final double TO_LAT = 36.88108, TO_LON = -116.81797; // BULLFROG stop
		assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON, (8 * 60 * 60) + (10 * 60));
	}

	@Test
	public void testRoute6() {
		final double FROM_LAT = 36.88108, FROM_LON = -116.81797; // BULLFROG stop
		final double TO_LAT = 36.914894, TO_LON = -116.76821; // NADAV stop
		assertNoRoute(graphHopper, FROM_LAT, FROM_LON, TO_LAT, TO_LON);
	}

	@Test
	public void testRoute7() {
		final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
		final double TO_LAT = 36.914894, TO_LON = -116.76821; // NADAV stop
		assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, (8 * 60 * 60) + (4 * 60), TO_LAT, TO_LON, (8 * 60 * 60) + (18 * 60));
	}

	private void assertRouteWeightIs(GraphHopperGtfs graphHopper, double from_lat, double from_lon, int earliestDepartureTime, double to_lat, double to_lon, int expectedWeight) {
		GHRequest ghRequest = new GHRequest(
				from_lat, from_lon,
				to_lat, to_lon
		);
		ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT, earliestDepartureTime);
		GHResponse route = graphHopper.route(ghRequest);
		System.out.println(route);
		System.out.println(route.getBest());
		System.out.println(route.getBest().getDebugInfo());

		assertFalse(route.hasErrors());
		assertEquals("Expected weight == scheduled arrival time", expectedWeight, route.getBest().getRouteWeight(), 0.1);
	}


	private void assertRouteWeightIs(GraphHopperGtfs graphHopper, double FROM_LAT, double FROM_LON, double TO_LAT, double TO_LON, int expectedWeight) {
		assertRouteWeightIs(graphHopper, FROM_LAT, FROM_LON, 0, TO_LAT, TO_LON, expectedWeight);
	}

	private void assertNoRoute(GraphHopperGtfs graphHopper, double from_lat, double from_lon, double to_lat, double to_lon) {
		GHRequest ghRequest = new GHRequest(
				from_lat, from_lon,
				to_lat, to_lon
		);

		GHResponse route = graphHopper.route(ghRequest);
		System.out.println(route);
		System.out.println(route.getBest());
		System.out.println(route.getBest().getDebugInfo());

		Assert.assertTrue(route.hasErrors());
	}

}
