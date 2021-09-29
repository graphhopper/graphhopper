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

package com.graphhopper.routing;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;

public class HeadingResolver {
    private final EdgeExplorer edgeExplorer;
    private double toleranceRad = Math.toRadians(100);

    public HeadingResolver(Graph graph) {
        this.edgeExplorer = graph.createEdgeExplorer();
    }

    /**
     * Returns a list of edge IDs of edges adjacent to the given base node that do *not* have the same or a similar
     * heading as the given heading. If for example the tolerance is 45 degrees this method returns all edges for which
     * the absolute difference to the given heading is greater than 45 degrees. The heading of an edge is defined as
     * the direction of the first segment of an edge (adjacent and facing away from the base node).
     *
     * @param heading north based azimuth, between 0 and 360 degrees
     * @see #setTolerance
     */
    public IntArrayList getEdgesWithDifferentHeading(int baseNode, double heading) {
        double xAxisAngle = AngleCalc.ANGLE_CALC.convertAzimuth2xaxisAngle(heading);
        IntArrayList edges = new IntArrayList(1);
        EdgeIterator iter = edgeExplorer.setBaseNode(baseNode);
        while (iter.next()) {
            PointList points = iter.fetchWayGeometry(FetchMode.ALL);
            double orientation = AngleCalc.ANGLE_CALC.calcOrientation(
                    points.getLat(0), points.getLon(0),
                    points.getLat(1), points.getLon(1)
            );

            orientation = AngleCalc.ANGLE_CALC.alignOrientation(xAxisAngle, orientation);
            double diff = Math.abs(orientation - xAxisAngle);

            if (diff > toleranceRad)
                edges.add(iter.getEdge());
        }
        return edges;
    }

    /**
     * This method returns true if the specified heading is parallel to the specified edgeState (antiparallel isn't tested).
     * Note that only the road segments near the specified pointNearHeading are checked for parallelism (<20m) and that
     * a angle difference of 30° is accepted.
     */
    public static boolean isHeadingNearlyParallel(EdgeIteratorState edgeState, double heading, GHPoint pointNearHeading) {
        DistanceCalc calcDist = DistanceCalcEarth.DIST_EARTH;
        double xAxisAngle = AngleCalc.ANGLE_CALC.convertAzimuth2xaxisAngle(heading);
        PointList points = edgeState.fetchWayGeometry(FetchMode.ALL);
        int closestPoint = -1;
        // TODO should we use the same default like for gpx_accuracy which is 40m?
        double closestDistance = 20; // skip road segments that are too far away from pointNearHeading
        double angleDifference = 30;
        for (int i = 1; i < points.size(); i++) {
            double fromLat = points.getLat(i - 1), fromLon = points.getLon(i - 1);
            double toLat = points.getLat(i), toLon = points.getLon(i);
            double distance = calcDist.validEdgeDistance(pointNearHeading.lat, pointNearHeading.lon, fromLat, fromLon, toLat, toLon)
                    ? calcDist.calcDenormalizedDist(calcDist.calcNormalizedEdgeDistance(pointNearHeading.lat, pointNearHeading.lon, fromLat, fromLon, toLat, toLon))
                    : calcDist.calcDist(fromLat, fromLon, pointNearHeading.lat, pointNearHeading.lon);

            if (distance <= closestDistance) {
                closestDistance = distance;
                closestPoint = i;
            }
        }
        if (closestPoint >= 0) {
            double fromLat = points.getLat(closestPoint - 1), fromLon = points.getLon(closestPoint - 1);
            double toLat = points.getLat(closestPoint), toLon = points.getLon(closestPoint);
            double orientation = AngleCalc.ANGLE_CALC.calcOrientation(fromLat, fromLon, toLat, toLon);
            orientation = AngleCalc.ANGLE_CALC.alignOrientation(xAxisAngle, orientation);
            return Math.abs(orientation - xAxisAngle) < Math.toRadians(angleDifference);
        }
        return false;
    }

    /**
     * Sets the tolerance for {@link #getEdgesWithDifferentHeading} in degrees.
     */
    public HeadingResolver setTolerance(double tolerance) {
        this.toleranceRad = Math.toRadians(tolerance);
        return this;
    }
}
