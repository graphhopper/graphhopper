/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
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
package com.graphhopper.util;

import com.graphhopper.util.shapes.GHPoint;

import java.util.Random;

/**
 * Projects coordinates to a new location using a given distance and bearing.
 */
public class CoordinateProjection
{

    private static final double EARTH_RADIUS_IN_KM = 6371;
    private static Random random = new Random();

    /**
     * This methods projects a point given in lat and long (in degrees) into a direction, given as bearing, measured
     * clockwise from north in degrees. The distance is passed in km.
     *
     * This formula is taken from: http://www.movable-type.co.uk/scripts/latlong.html
     *
     *   lat2 = asin( sin φ1 ⋅ cos δ + cos φ1 ⋅ sin δ ⋅ cos θ )
     *   lon2 = λ1 + atan2( sin θ ⋅ sin δ ⋅ cos φ1, cos δ − sin φ1 ⋅ sin φ2 )
     *
     */
    public static GHPoint projectCoordinate( double latInDeg, double lonInDeg, double distanceInKm, double bearingClockwiseFromNorth )
    {
        double angularDistance = distanceInKm / EARTH_RADIUS_IN_KM;

        double latInRadians = Math.toRadians(latInDeg);
        double lonInRadians = Math.toRadians(lonInDeg);
        double bearingInRadians = Math.toRadians(bearingClockwiseFromNorth);

        double projectedLat = Math.asin(Math.sin(latInRadians) * Math.cos(angularDistance) +
                Math.cos(latInRadians) * Math.sin(angularDistance) * Math.cos(bearingInRadians));
        double projectedLon = lonInRadians + Math.atan2(Math.sin(bearingInRadians) * Math.sin(angularDistance) * Math.cos(latInRadians),
                Math.cos(angularDistance) - Math.sin(latInRadians) * Math.sin(projectedLat));

        projectedLon = (projectedLon + 3 * Math.PI) % (2 * Math.PI) - Math.PI; // normalise to -180..+180°

        projectedLat = Math.toDegrees(projectedLat);
        projectedLon = Math.toDegrees(projectedLon);

        return new GHPoint(projectedLat, projectedLon);
    }

    public static GHPoint projectCoordinateRandomBearingAndModifiedDistance( double latInDeg, double lonInDeg, double distanceInKm )
    {
        double bearing = random.nextInt(360);
        double distanceModification = random.nextDouble() * .1 * distanceInKm;
        if (random.nextBoolean())
            distanceModification = -distanceModification;
        distanceInKm = distanceInKm + distanceModification;
        return projectCoordinate(latInDeg, lonInDeg, distanceInKm, bearing);
    }

}
