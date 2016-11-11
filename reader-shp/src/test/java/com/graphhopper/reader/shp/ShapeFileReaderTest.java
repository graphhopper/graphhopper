package com.graphhopper.reader.shp;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.reader.DataReader;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;

/**
 * Tests the ShapeFileReader with the normal helper initialized.
 * <p>
 * 
 * @author Vikas Veshishth
 */
public class ShapeFileReaderTest {

	private final String shapefile = "/shapefiles/gis.osm_roads_free_1.shp";
	private final String tempOutputDirectory = "./target/tmp/test-db";
	// private CarFlagEncoder carEncoder;

	@Before
	public void setUp() {
		new File(tempOutputDirectory).mkdirs();
	}

	@After
	public void tearDown() {
		Helper.removeDir(new File(tempOutputDirectory));
	}


	private static class ExpectedDuration {
		final GHPoint from;
		final GHPoint to;
		final double minSecs;
		final double maxSecs;

		private ExpectedDuration(double fromLat, double fromLng, double toLat, double toLng, double minSecs, double maxSecs) {
			from = new GHPoint(fromLat, fromLng);
			to = new GHPoint(toLat, toLng);
			this.minSecs = minSecs;
			this.maxSecs = maxSecs;
		}
	}

	@Test
	public void testMain() throws URISyntaxException {
		// test we can make a graph without any exceptions....
		URL resourceURL = getClass().getResource(shapefile);
		String shapeFile = new File(resourceURL.toURI()).getAbsolutePath();

		GraphHopper hopper = new GraphhopperShp().setStoreOnFlush(false).setDataReaderFile(shapeFile)
				.setGraphHopperLocation(tempOutputDirectory).setEncodingManager(new EncodingManager(new CarFlagEncoder()))
				.setCHEnabled(false).importOrLoad();

		// try a couple of test points, with an expected time range that will only fail if something is really bad...
		ExpectedDuration[] expected = new ExpectedDuration[] {
				new ExpectedDuration(35.899167, 14.515171, 35.894126, 14.502983, 60, 60 * 6) ,
				new ExpectedDuration(35.899167, 14.515171, 35.877645,14.398956, 8*60, 25*60 ),
				new ExpectedDuration(35.85817,14.561348, 35.877645,14.398956, 10*60, 30*60 ),
				new ExpectedDuration(35.812802,14.528732, 35.979673,14.335785, 20*60, 50*60 ),

		};
		for (ExpectedDuration ed : expected) {
			GHRequest request = new GHRequest(ed.from, ed.to).setVehicle("car");
			GHResponse response = hopper.route(request);
			assertFalse(response.hasErrors());
			PathWrapper pw = response.getBest();
			assertNotNull(pw);
			long millis = pw.getTime();
			double secs = 0.001 * millis;

			String message = "From (" + ed.from + ") to (" + ed.to + ") expected " + ed.minSecs + " <= travelsecs <= " + ed.maxSecs
					+ ", found " + secs + " secs";
			assertTrue(message, secs >= ed.minSecs);
			assertTrue(message, secs <= ed.maxSecs);
			System.out.println(message);
		}

		// assertEquals(2917, graph.getNodes());
	}

}
