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

import com.graphhopper.core.util.shapes.BBox;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

/**
 * Calculates the distance of two points or one point and an edge on earth via haversine formula.
 * Allows subclasses to implement less or more precise calculations.
 * <p>
 * See http://en.wikipedia.org/wiki/Haversine_formula
 *
 * @author Peter Karich
 */
public interface DistanceCalc {
    BBox createBBox(double lat, double lon, double radiusInMeter);

    double calcCircumference(double lat);

    /**
     * Calculates distance of (from, to) in meter.
     */
    double calcDist(double fromLat, double fromLon, double toLat, double toLon);

    /**
     * Calculates 3d distance of (from, to) in meter.
     */
    double calcDist3D(double fromLat, double fromLon, double fromEle, double toLat, double toLon, double toEle);

    /**
     * Returns the specified length in normalized meter.
     */
    double calcNormalizedDist(double dist);

    /**
     * Inverse to calcNormalizedDist. Returned the length in meter.
     */
    double calcDenormalizedDist(double normedDist);

    /**
     * Calculates in normalized meter
     */
    double calcNormalizedDist(double fromLat, double fromLon, double toLat, double toLon);

    /**
     * This method decides for case 1: if we should use distance(r to edge) where r=(lat,lon) or
     * case 2: min(distance(r to a), distance(r to b)) where edge=(a to b). Note that due to
     * rounding errors it cannot properly detect if it is case 1 or 90°.
     * <pre>
     * case 1 (including ):
     *   r
     *  .
     * a-------b
     * </pre>
     * <pre>
     * case 2:
     * r
     *  .
     *    a-------b
     * </pre>
     *
     * @return true for case 1 which is "on edge" or the special case of 90° to the edge
     */
    boolean validEdgeDistance(double r_lat_deg, double r_lon_deg,
                              double a_lat_deg, double a_lon_deg,
                              double b_lat_deg, double b_lon_deg);

    /**
     * This method calculates the distance from r to edge (a, b) where the crossing point is c
     *
     * @return the distance in normalized meter
     */
    double calcNormalizedEdgeDistance(double r_lat_deg, double r_lon_deg,
                                      double a_lat_deg, double a_lon_deg,
                                      double b_lat_deg, double b_lon_deg);

    /**
     * This method calculates the distance from r to edge (a, b) where the crossing point is c including elevation
     *
     * @return the distance in normalized meter
     */
    double calcNormalizedEdgeDistance3D(double r_lat_deg, double r_lon_deg, double r_ele_m,
                                        double a_lat_deg, double a_lon_deg, double a_ele_m,
                                        double b_lat_deg, double b_lon_deg, double b_ele_m);

    /**
     * @return the crossing point c of the vertical line from r to line (a, b)
     */
    GHPoint calcCrossingPointToEdge(double r_lat_deg, double r_lon_deg,
                                    double a_lat_deg, double a_lon_deg,
                                    double b_lat_deg, double b_lon_deg);

    /**
     * This methods creates a point (lat, lon in degrees) in a certain distance and direction from the specified
     * point (lat, lon in degrees). The heading is measured clockwise from north in degrees. The distance is passed in meter.
     */
    GHPoint projectCoordinate(double lat, double lon,
                              double distanceInMeter, double headingClockwiseFromNorth);

    /**
     * This methods creates a point (lat, lon in degrees) a fraction of the distance along the path from (lat1, lon1)
     * to (lat2, lon2).
     */
    GHPoint intermediatePoint(double f, double lat1, double lon1, double lat2, double lon2);

    /*
     * Simple heuristic to detect if the specified two points are crossing the boundary +-180°. See
     * #667
     */
    boolean isCrossBoundary(double lon1, double lon2);

    double calcDistance(PointList pointList);

}
