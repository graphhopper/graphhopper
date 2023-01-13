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
package com.graphhopper.core.util;

import com.graphhopper.core.util.DistancePlaneProjection;
import com.graphhopper.core.util.DistanceCalcEarth;
import com.graphhopper.core.util.DistanceCalc;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class DistanceCalcEarthTest {
    private DistanceCalc dc = new DistanceCalcEarth();

    @Test
    public void testCalcCircumference() {
        assertEquals(DistanceCalcEarth.C, dc.calcCircumference(0), 1e-7);
    }

    @Test
    public void testDistance() {
        float lat = 24.235f;
        float lon = 47.234f;
        DistanceCalc approxDist = new DistancePlaneProjection();
        double res = 15051;
        assertEquals(res, dc.calcDist(lat, lon, lat - 0.1, lon + 0.1), 1);
        assertEquals(dc.calcNormalizedDist(res), dc.calcNormalizedDist(lat, lon, lat - 0.1, lon + 0.1), 1);
        assertEquals(res, approxDist.calcDist(lat, lon, lat - 0.1, lon + 0.1), 1);

        res = 15046;
        assertEquals(res, dc.calcDist(lat, lon, lat + 0.1, lon - 0.1), 1);
        assertEquals(dc.calcNormalizedDist(res), dc.calcNormalizedDist(lat, lon, lat + 0.1, lon - 0.1), 1);
        assertEquals(res, approxDist.calcDist(lat, lon, lat + 0.1, lon - 0.1), 1);

        res = 150748;
        assertEquals(res, dc.calcDist(lat, lon, lat - 1, lon + 1), 1);
        assertEquals(dc.calcNormalizedDist(res), dc.calcNormalizedDist(lat, lon, lat - 1, lon + 1), 1);
        assertEquals(res, approxDist.calcDist(lat, lon, lat - 1, lon + 1), 10);

        res = 150211;
        assertEquals(res, dc.calcDist(lat, lon, lat + 1, lon - 1), 1);
        assertEquals(dc.calcNormalizedDist(res), dc.calcNormalizedDist(lat, lon, lat + 1, lon - 1), 1);
        assertEquals(res, approxDist.calcDist(lat, lon, lat + 1, lon - 1), 10);

        res = 1527919;
        assertEquals(res, dc.calcDist(lat, lon, lat - 10, lon + 10), 1);
        assertEquals(dc.calcNormalizedDist(res), dc.calcNormalizedDist(lat, lon, lat - 10, lon + 10), 1);
        assertEquals(res, approxDist.calcDist(lat, lon, lat - 10, lon + 10), 10000);

        res = 1474016;
        assertEquals(res, dc.calcDist(lat, lon, lat + 10, lon - 10), 1);
        assertEquals(dc.calcNormalizedDist(res), dc.calcNormalizedDist(lat, lon, lat + 10, lon - 10), 1);
        assertEquals(res, approxDist.calcDist(lat, lon, lat + 10, lon - 10), 10000);

        res = 1013735.28;
        assertEquals(res, dc.calcDist(lat, lon, lat, lon - 10), 1);
        assertEquals(dc.calcNormalizedDist(res), dc.calcNormalizedDist(lat, lon, lat, lon - 10), 1);
        // 1013952.659
        assertEquals(res, approxDist.calcDist(lat, lon, lat, lon - 10), 1000);

        // if we have a big distance for latitude only then PlaneProjection is exact!!
        res = 1111949.3;
        assertEquals(res, dc.calcDist(lat, lon, lat + 10, lon), 1);
        assertEquals(dc.calcNormalizedDist(res), dc.calcNormalizedDist(lat, lon, lat + 10, lon), 1);
        assertEquals(res, approxDist.calcDist(lat, lon, lat + 10, lon), 1);
    }

    @Test
    public void testEdgeDistance() {
        double dist = dc.calcNormalizedEdgeDistance(49.94241, 11.544356,
                49.937964, 11.541824,
                49.942272, 11.555643);
        double expectedDist = dc.calcNormalizedDist(49.94241, 11.544356,
                49.9394, 11.54681);
        assertEquals(expectedDist, dist, 1e-4);

        // test identical lats
        dist = dc.calcNormalizedEdgeDistance(49.936299, 11.543992,
                49.9357, 11.543047,
                49.9357, 11.549227);
        expectedDist = dc.calcNormalizedDist(49.936299, 11.543992,
                49.9357, 11.543992);
        assertEquals(expectedDist, dist, 1e-4);
    }

    @Test
    public void testEdgeDistance3d() {
        double dist = dc.calcNormalizedEdgeDistance3D(49.94241, 11.544356, 0,
                49.937964, 11.541824, 0,
                49.942272, 11.555643, 0);
        double expectedDist = dc.calcNormalizedDist(49.94241, 11.544356,
                49.9394, 11.54681);
        assertEquals(expectedDist, dist, 1e-4);

        // test identical lats
        dist = dc.calcNormalizedEdgeDistance3D(49.936299, 11.543992, 0,
                49.9357, 11.543047, 0,
                49.9357, 11.549227, 0);
        expectedDist = dc.calcNormalizedDist(49.936299, 11.543992,
                49.9357, 11.543992);
        assertEquals(expectedDist, dist, 1e-4);
    }

    @Test
    public void testEdgeDistance3dEarth() {
        double dist = dc.calcNormalizedEdgeDistance3D(0, 0.5, 10,
                0, 0, 0,
                0, 1, 0);
        assertEquals(10, dc.calcDenormalizedDist(dist), 1e-4);
    }

    @Test
    public void testEdgeDistance3dEarthNaN() {
        double dist = dc.calcNormalizedEdgeDistance3D(0, 0.5, Double.NaN,
                0, 0, 0,
                0, 1, 0);
        assertEquals(0, dc.calcDenormalizedDist(dist), 1e-4);
    }

    @Test
    public void testEdgeDistance3dPlane() {
        DistanceCalc calc = new DistancePlaneProjection();
        double dist = calc.calcNormalizedEdgeDistance3D(0, 0.5, 10,
                0, 0, 0,
                0, 1, 0);
        assertEquals(10, calc.calcDenormalizedDist(dist), 1e-4);
    }

    @Test
    public void testEdgeDistanceStartEndSame() {
        DistanceCalc calc = new DistancePlaneProjection();
        // just change elevation
        double dist = calc.calcNormalizedEdgeDistance3D(0, 0, 10,
                0, 0, 0,
                0, 0, 0);
        assertEquals(10, calc.calcDenormalizedDist(dist), 1e-4);
        // just change lat
        dist = calc.calcNormalizedEdgeDistance3D(1, 0, 0,
                0, 0, 0,
                0, 0, 0);
        assertEquals(DistanceCalcEarth.METERS_PER_DEGREE, calc.calcDenormalizedDist(dist), 1e-4);
        // just change lon
        dist = calc.calcNormalizedEdgeDistance3D(0, 1, 0,
                0, 0, 0,
                0, 0, 0);
        assertEquals(DistanceCalcEarth.METERS_PER_DEGREE, calc.calcDenormalizedDist(dist), 1e-4);
    }

    @Test
    public void testEdgeDistanceStartEndDifferentElevation() {
        DistanceCalc calc = new DistancePlaneProjection();
        // just change elevation
        double dist = calc.calcNormalizedEdgeDistance3D(0, 0, 10,
                0, 0, 0,
                0, 0, 1);
        assertEquals(0, calc.calcDenormalizedDist(dist), 1e-4);
        // just change lat
        dist = calc.calcNormalizedEdgeDistance3D(1, 0, 0,
                0, 0, 0,
                0, 0, 1);
        assertEquals(DistanceCalcEarth.METERS_PER_DEGREE, calc.calcDenormalizedDist(dist), 1e-4);
        // just change lon
        dist = calc.calcNormalizedEdgeDistance3D(0, 1, 0,
                0, 0, 0,
                0, 0, 1);
        assertEquals(DistanceCalcEarth.METERS_PER_DEGREE, calc.calcDenormalizedDist(dist), 1e-4);
    }

    @Test
    public void testValidEdgeDistance() {
        assertTrue(dc.validEdgeDistance(49.94241, 11.544356, 49.937964, 11.541824, 49.942272, 11.555643));
        assertTrue(dc.validEdgeDistance(49.936624, 11.547636, 49.937964, 11.541824, 49.942272, 11.555643));
        assertTrue(dc.validEdgeDistance(49.940712, 11.556069, 49.937964, 11.541824, 49.942272, 11.555643));

        // left bottom of the edge
        assertFalse(dc.validEdgeDistance(49.935119, 11.541649, 49.937964, 11.541824, 49.942272, 11.555643));
        // left top of the edge
        assertFalse(dc.validEdgeDistance(49.939317, 11.539675, 49.937964, 11.541824, 49.942272, 11.555643));
        // right top of the edge
        assertFalse(dc.validEdgeDistance(49.944482, 11.555446, 49.937964, 11.541824, 49.942272, 11.555643));
        // right bottom of the edge
        assertFalse(dc.validEdgeDistance(49.94085, 11.557356, 49.937964, 11.541824, 49.942272, 11.555643));

        // rounding error
        // assertFalse(dc.validEdgeDistance(0.001, 0.001, 0.001, 0.002, 0.00099987, 0.00099987));
    }

    @Test
    public void testPrecisionBug() {
        DistanceCalc dist = new DistancePlaneProjection();
//        DistanceCalc dist = new DistanceCalc();
        double queryLat = 42.56819, queryLon = 1.603231;
        double lat16 = 42.56674481705006, lon16 = 1.6023790821964834;
        double lat17 = 42.56694505140808, lon17 = 1.6020622462495173;
        double lat18 = 42.56715199128878, lon18 = 1.601682266630581;

        // segment 18
        assertEquals(171.487, dist.calcDist(queryLat, queryLon, lat18, lon18), 1e-3);
        // segment 17
        assertEquals(168.298, dist.calcDist(queryLat, queryLon, lat17, lon17), 1e-3);
        // segment 16
        assertEquals(175.188, dist.calcDist(queryLat, queryLon, lat16, lon16), 1e-3);

        assertEquals(167.385, dist.calcDenormalizedDist(dist.calcNormalizedEdgeDistance(queryLat, queryLon, lat16, lon16, lat17, lon17)), 1e-3);

        assertEquals(168.213, dist.calcDenormalizedDist(dist.calcNormalizedEdgeDistance(queryLat, queryLon, lat17, lon17, lat18, lon18)), 1e-3);

        // 16_17
        assertEquals(new GHPoint(42.567048, 1.6019), dist.calcCrossingPointToEdge(queryLat, queryLon, lat16, lon16, lat17, lon17));
        // 17_18
        // assertEquals(new GHPoint(42.566945,1.602062), dist.calcCrossingPointToEdge(queryLat, queryLon, lat17, lon17, lat18, lon18));
    }

    @Test
    public void testPrecisionBug2() {
        DistanceCalc distCalc = new DistancePlaneProjection();
        double queryLat = 55.818994, queryLon = 37.595354;
        double tmpLat = 55.81777239183573, tmpLon = 37.59598350366913;
        double wayLat = 55.818839128736535, wayLon = 37.5942968784488;
        assertEquals(68.25, distCalc.calcDist(wayLat, wayLon, queryLat, queryLon), .1);

        assertEquals(60.88, distCalc.calcDenormalizedDist(distCalc.calcNormalizedEdgeDistance(queryLat, queryLon,
                tmpLat, tmpLon, wayLat, wayLon)), .1);

        assertEquals(new GHPoint(55.81863, 37.594626), distCalc.calcCrossingPointToEdge(queryLat, queryLon,
                tmpLat, tmpLon, wayLat, wayLon));
    }

    @Test
    public void testDistance3dEarth() {
        DistanceCalc distCalc = new DistanceCalcEarth();
        assertEquals(1, distCalc.calcDist3D(
                0, 0, 0,
                0, 0, 1
        ), 1e-6);
    }

    @Test
    public void testDistance3dEarthNaN() {
        DistanceCalc distCalc = new DistanceCalcEarth();
        assertEquals(0, distCalc.calcDist3D(
                0, 0, 0,
                0, 0, Double.NaN
        ), 1e-6);
        assertEquals(0, distCalc.calcDist3D(
                0, 0, Double.NaN,
                0, 0, 10
        ), 1e-6);
        assertEquals(0, distCalc.calcDist3D(
                0, 0, Double.NaN,
                0, 0, Double.NaN
        ), 1e-6);
    }

    @Test
    public void testDistance3dPlane() {
        DistancePlaneProjection distCalc = new DistancePlaneProjection();
        assertEquals(1, distCalc.calcDist3D(
                0, 0, 0,
                0, 0, 1
        ), 1e-6);
        assertEquals(10, distCalc.calcDist3D(
                0, 0, 0,
                0, 0, 10
        ), 1e-6);
    }

    @Test
    public void testDistance3dPlaneNaN() {
        DistancePlaneProjection distCalc = new DistancePlaneProjection();
        assertEquals(0, distCalc.calcDist3D(
                0, 0, 0,
                0, 0, Double.NaN
        ), 1e-6);
        assertEquals(0, distCalc.calcDist3D(
                0, 0, Double.NaN,
                0, 0, 10
        ), 1e-6);
        assertEquals(0, distCalc.calcDist3D(
                0, 0, Double.NaN,
                0, 0, Double.NaN
        ), 1e-6);
    }

    @Test
    public void testIntermediatePoint() {
        DistanceCalc distCalc = new DistanceCalcEarth();
        GHPoint point = distCalc.intermediatePoint(0, 0, 0, 0, 0);
        assertEquals(0, point.getLat(), 1e-5);
        assertEquals(0, point.getLon(), 1e-5);

        point = distCalc.intermediatePoint(0.5, 0, 0, 10, 0);
        assertEquals(5, point.getLat(), 1e-5);
        assertEquals(0, point.getLon(), 1e-5);

        point = distCalc.intermediatePoint(0.5, 0, 0, 0, 10);
        assertEquals(0, point.getLat(), 1e-5);
        assertEquals(5, point.getLon(), 1e-5);

        // cross international date line going west
        point = distCalc.intermediatePoint(0.5, 45, -179, 45, 177);
        assertEquals(45, point.getLat(), 1);
        assertEquals(179, point.getLon(), 1e-5);

        // cross international date line going east
        point = distCalc.intermediatePoint(0.5, 45, 179, 45, -177);
        assertEquals(45, point.getLat(), 1);
        assertEquals(-179, point.getLon(), 1e-5);

        // cross north pole
        point = distCalc.intermediatePoint(0.25, 45, -90, 45, 90);
        assertEquals(67.5, point.getLat(), 1e-1);
        assertEquals(-90, point.getLon(), 1e-5);
        point = distCalc.intermediatePoint(0.75, 45, -90, 45, 90);
        assertEquals(67.5, point.getLat(), 1e-1);
        assertEquals(90, point.getLon(), 1e-5);
    }
}
