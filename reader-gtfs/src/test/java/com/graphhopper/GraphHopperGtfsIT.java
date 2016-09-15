package com.graphhopper;

import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class GraphHopperGtfsIT {

	private static final String graphFileFoot = "target/graphhopperIT-foot";
	private final DistanceCalc distCalc = Helper.DIST_EARTH;


	@Test
	public void testLoadGtfs() {
		Helper.removeDir(new File(graphFileFoot));

		GraphHopperGtfs graphHopper = new GraphHopperGtfs();
		graphHopper.setCHEnabled(false);
		graphHopper.setGtfsFile("files/sample-feed.zip");
		graphHopper.setGraphHopperLocation(graphFileFoot);
		graphHopper.importOrLoad();

		final double FROM_LAT = 36.914893, FROM_LON = -116.76821; // NADAV stop
		final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop

		GHRequest ghRequest = new GHRequest(
				FROM_LAT, FROM_LON,
				TO_LAT, TO_LON
		);


		GHResponse route = graphHopper.route(ghRequest);
		System.out.println(route);
		PathWrapper best = route.getBest();

		double distanceMeters = best.getDistance();
		double travelTimeSeconds = best.getTime() / 1000.0;

		System.out.println(best);
		System.out.println(best.getDebugInfo());
		assertEquals("Expected weight == scheduled arrival time", (6 * 60 * 60) + (49 * 60), best.getRouteWeight(), 0.1);

		assertEquals("Travel distance between stops", distCalc.calcDist(FROM_LAT, FROM_LON, TO_LAT, TO_LON), distanceMeters, 0.1);

	}

	@Test
	public void testLoadGtfs2() {
		Helper.removeDir(new File(graphFileFoot));

		GraphHopperGtfs graphHopper = new GraphHopperGtfs();
		graphHopper.setCHEnabled(false);
		graphHopper.setGtfsFile("files/sample-feed.zip");
		graphHopper.setGraphHopperLocation(graphFileFoot);
		graphHopper.importOrLoad();

		final double FROM_LAT = 36.914894, FROM_LON = -116.76821; // NADAV stop
		final double TO_LAT = 36.909489, TO_LON = -116.768242; // DADAN stop

		GHRequest ghRequest = new GHRequest(
				FROM_LAT, FROM_LON,
				TO_LAT, TO_LON
		);


		GHResponse route = graphHopper.route(ghRequest);
		PathWrapper best = route.getBest();

		double distanceMeters = best.getDistance();
		double travelTimeSeconds = best.getTime() / 1000.0;

		System.out.println(best);
		System.out.println(best.getDebugInfo());
		assertEquals("Expected weight == scheduled travel time", (6 * 60 * 60) + (19 * 60), best.getRouteWeight(), 0.1);

		assertEquals("Travel distance between stops", distCalc.calcDist(FROM_LAT, FROM_LON, TO_LAT, TO_LON), distanceMeters, 1.0);

	}

	@Test
	public void testLoadGtfs3() {
		Helper.removeDir(new File(graphFileFoot));

		GraphHopperGtfs graphHopper = new GraphHopperGtfs();
		graphHopper.setCHEnabled(false);
		graphHopper.setGtfsFile("files/sample-feed.zip");
		graphHopper.setGraphHopperLocation(graphFileFoot);
		graphHopper.importOrLoad();

		final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
		final double TO_LAT = 36.914944, TO_LON = -116.761472; // NANAA stop

		GHRequest ghRequest = new GHRequest(
				FROM_LAT, FROM_LON,
				TO_LAT, TO_LON
		);


		GHResponse route = graphHopper.route(ghRequest);
		PathWrapper best = route.getBest();

		double distanceMeters = best.getDistance();
		double travelTimeSeconds = best.getTime() / 1000.0;

		System.out.println(best);
		System.out.println(best.getDebugInfo());
		assertEquals("Expected weight == scheduled travel time", (6 * 60 * 60) + (5 * 60), best.getRouteWeight(), 0.1);

		assertEquals("Travel distance between stops", distCalc.calcDist(FROM_LAT, FROM_LON, TO_LAT, TO_LON), distanceMeters, 1.0);

	}

	@Test
	public void testLoadGtfs4() {
		Helper.removeDir(new File(graphFileFoot));

		GraphHopperGtfs graphHopper = new GraphHopperGtfs();
		graphHopper.setCHEnabled(false);
		graphHopper.setGtfsFile("files/sample-feed.zip");
		graphHopper.setGraphHopperLocation(graphFileFoot);
		graphHopper.importOrLoad();

		final double FROM_LAT = 36.915682, FROM_LON = -116.751677; // STAGECOACH stop
		final double TO_LAT = 36.914894, TO_LON = -116.76821; // NADAV stop

		GHRequest ghRequest = new GHRequest(
				FROM_LAT, FROM_LON,
				TO_LAT, TO_LON
		);


		GHResponse route = graphHopper.route(ghRequest);
		PathWrapper best = route.getBest();

		double distanceMeters = best.getDistance();
		double travelTimeSeconds = best.getTime() / 1000.0;

		System.out.println(best);
		System.out.println(best.getDebugInfo());
		assertEquals("Expected weight == scheduled travel time", (6 * 60 * 60) + (12 * 60), best.getRouteWeight(), 0.1);

		assertEquals("Travel distance between stops", distCalc.calcDist(FROM_LAT, FROM_LON, TO_LAT, TO_LON), distanceMeters, 10.0);

	}


}
