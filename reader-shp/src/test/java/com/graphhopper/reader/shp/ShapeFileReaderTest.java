/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.reader.shp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.DoubleSummaryStatistics;
import java.util.Random;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

/**
 *
 * @author Vikas Veshishth
 * @author Philip Welch
 */
public class ShapeFileReaderTest {

    private static final String shapefile = "/data/gis.osm_roads_free_1.shp";
    private static final String pbf = "/data/malta-latest.osm.pbf";
    private static final String tempOutputDirFromShp = "target/test-db-shp";
    private static final String tempOutputDirFromPbf = "target/test-db-pbf";
    private static GraphHopper hopperShp;
    private static GraphHopper hopperPbf;
    private final DistanceCalc distCalc = Helper.DIST_EARTH;
    private static Exception BEFORE_CLASS_EXCEPTION = null;

    private static class FromToPair {
        final GHPoint from;
        final GHPoint to;

        FromToPair(double fromLat, double fromLng, double toLat, double toLng) {
            this(new GHPoint(fromLat, fromLng), new GHPoint(toLat, toLng));
        }

        FromToPair(GHPoint from, GHPoint to) {
            this.from = from;
            this.to = to;
        }

        PathWrapper getPath(GraphHopper hopper, boolean assertNoErrors) {
            GHRequest request = new GHRequest(from, to).setVehicle("car");
            GHResponse response = hopper.route(request);

            if (assertNoErrors) {
                assertFalse(response.hasErrors());
            }

            if (!response.hasErrors()) {
                return response.getBest();
            }
            return null;
        }

    }

    private static class ExpectedDuration extends FromToPair {
        final double minSecs;
        final double maxSecs;

        private ExpectedDuration(double fromLat, double fromLng, double toLat, double toLng,
                                 double minSecs, double maxSecs) {
            super(fromLat, fromLng, toLat, toLng);
            this.minSecs = minSecs;
            this.maxSecs = maxSecs;
        }
    }

    private static GraphHopper initHopper(GraphHopper gh, String inputFile, String outDir) {
        URL resourceURL = ShapeFileReaderTest.class.getResource(inputFile);
        try {
            inputFile = new File(resourceURL.toURI()).getAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // turn off geometry simplification so geometry should be the same
        // between pbf and shapefile readers
        gh.setWayPointMaxDistance(0);
        return gh.setStoreOnFlush(false).setDataReaderFile(inputFile)
                .setGraphHopperLocation(new File(outDir).getAbsolutePath())
                .setEncodingManager(new EncodingManager(new CarFlagEncoder()))
                .setCHEnabled(false).importOrLoad();

    }

    /**
     * Build the graphs once only for the various tests
     */
    @BeforeClass
    public static void setupBeforeClass() {
        try {
            new File(tempOutputDirFromShp).mkdirs();
            new File(tempOutputDirFromPbf).mkdirs();

            hopperShp = initHopper(new GraphhopperSHP(), shapefile, tempOutputDirFromShp);

            hopperPbf = initHopper(new GraphHopperOSM(), pbf, tempOutputDirFromPbf);

        } catch (Exception e) {
            // Junit silently fails if we get an exception in the setup before
            // class,
            // so we record it here and explicitly rethrow it
            BEFORE_CLASS_EXCEPTION = e;
        }

    }

    @AfterClass
    public static void teardownAfterClass() {
        try {
            hopperShp.close();
            hopperShp.clean();
        } catch (Exception e) {
        }

        try {
            hopperPbf.close();
            hopperPbf.clean();
        } catch (Exception e) {
        }

    }

    @Before
    public void beforeTest() throws Exception {
        // Rethrow the exception from @BeforeClass here so it doesn't silently
        // fail.
        // (Junit silently fails on exceptions thrown in @BeforeClass but not
        // for
        // exceptions thrown in @Before)
        if (BEFORE_CLASS_EXCEPTION != null) {
            throw BEFORE_CLASS_EXCEPTION;
        }
    }

    @Test
    public void testOneWay() {
        // We setup 2 points very close together on a one-way street.
        // As its a one way street, the ordering of the start and end requires
        // going around the block to serve them.
        // As the scenario is simple, we should get the same results from both
        // shapefile and pbf.
        // We should also get route distance to be many times physical distance
        FromToPair pair = new FromToPair(35.898324, 14.510729, 35.898328, 14.510681);
        PathWrapper shp = pair.getPath(hopperShp, true);
        PathWrapper pbf = pair.getPath(hopperPbf, true);
        double metresShp = shp.getDistance();
        double metresPbf = pbf.getDistance();

        // should be many times the physical separation between the points (as
        // we had to go round the block)
        double straightLineDistMetres = distCalc.calcDist(pair.from.lat, pair.from.lon, pair.to.lat,
                pair.to.lon);
        assertTrue(metresShp > straightLineDistMetres * 25);

        // should be the same to within 1 cm
        assertEquals(metresShp, metresPbf, 0.01);

    }

    @Test
    public void testGeometrySingleEdgePath() {
        // We choose a path along a single edge with a couple of minor bends,
        // which we expect to give identical results...
        FromToPair pair = new FromToPair(35.911694, 14.492303, 35.911494, 14.490489);
        PointList shp = pair.getPath(hopperShp, true).getPoints();
        PointList pbf = pair.getPath(hopperPbf, true).getPoints();

        assertTrue("The chosen edge had a couple of bends!", shp.getSize() >= 2);
        assertSameGeometry(shp, pbf);
    }

    private void assertSameGeometry(PointList shp, PointList pbf) {
        assertEquals(shp.getSize(), pbf.getSize());

        for (int i = 0; i < shp.getSize(); i++) {
            assertEquals(shp.getLat(i), pbf.getLat(i), 0.0000001);
            assertEquals(shp.getLon(i), pbf.getLon(i), 0.0000001);
        }
    }

    @Test
    public void testTravelTimesBetweenRandomLocations() {
        int nTests = 200;
        final Random random = new Random(123);
        final GHPoint min = new GHPoint(35.882931, 14.403076);
        final GHPoint max = new GHPoint(35.913523, 14.448566);

        class RandPointGenerator {
            double rand(double min, double max) {
                return min + random.nextDouble() * (max - min);
            }

            GHPoint randPoint() {
                return new GHPoint(rand(min.lat, max.lat), rand(min.lon, max.lon));
            }

        }
        RandPointGenerator pointGenerator = new RandPointGenerator();

        int nbFails = 0;
        DoubleSummaryStatistics stats = new DoubleSummaryStatistics();
        for (int i = 0; i < nTests; i++) {
            FromToPair pair = new FromToPair(pointGenerator.randPoint(),
                    pointGenerator.randPoint());

            // paths from random points can fail to don't assert on failure
            PathWrapper shpPath = pair.getPath(hopperShp, false);
            PathWrapper pbfPath = pair.getPath(hopperPbf, false);

            // paths between random points can fail to find a route (i.e. be off
            // the road network)
            if (shpPath == null || pbfPath == null) {
                nbFails++;
                continue;
            }
            double shpSecs = getSecondsTravel(shpPath);
            double pbfSecs = getSecondsTravel(pbfPath);

            double frac = shpSecs / pbfSecs;
            double percentageDeviation = Math.abs(1.0 - frac) * 100;
            stats.accept(percentageDeviation);
        }

        assertTrue("Number of fails should be small for the chosen box", nbFails < nTests / 3);

        // Test mean fraction. There will be some deviation as not all tags are
        // considered etc,
        // but we expect it to be small for a large number of tests
        double mean = stats.getAverage();
        assertTrue("Should have a mean deviation in travel times of less than 1%", mean < 1.0);
    }

    @Test
    public void testTravelTimesBetweenPredefinedLocations() throws URISyntaxException {

        // try a couple of test points, with an expected time range that will
        // only fail if something is really bad...
        ExpectedDuration[] expected = new ExpectedDuration[]{
            new ExpectedDuration(35.899167, 14.515171, 35.894126, 14.502983, 60,
            60 * 6),
            new ExpectedDuration(35.899167, 14.515171, 35.877645, 14.398956, 8 * 60,
            25 * 60),
            new ExpectedDuration(35.85817, 14.561348, 35.877645, 14.398956, 10 * 60,
            30 * 60),
            new ExpectedDuration(35.812802, 14.528732, 35.979673, 14.335785, 20 * 60,
            50 * 60),};

        // The chosen locations should have small deviations in travel times
        double tolDiffFromPbf = 0.01;

        for (ExpectedDuration ed : expected) {
            double secsShp = getSecondsTravel(ed.getPath(hopperShp, true));
            double secsPbf = getSecondsTravel(ed.getPath(hopperPbf, true));
            double frac = secsShp / secsPbf;

            String message = "From (" + ed.from + ") to (" + ed.to + ") expected " + ed.minSecs
                    + " <= travelsecs <= " + ed.maxSecs + ", found " + secsShp
                    + " secs, pbf was " + secsPbf + " secs, frac diff=" + frac;
            assertTrue(message, secsShp >= ed.minSecs);
            assertTrue(message, secsShp <= ed.maxSecs);

            // we also use a tolerance difference with the pbf
            assertTrue(frac > 1 - tolDiffFromPbf);
            assertTrue(frac < 1 + tolDiffFromPbf);

        }

    }

    private static double getSecondsTravel(PathWrapper pw) {
        long millis = pw.getTime();
        double secs = 0.001 * millis;
        return secs;
    }

}
