/*
 * Copyright 2014 Kromm.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.graphhopper.util;

/**
 * Calculates the angle of a turn, defined by three points.
 * 
 * Like distance calculation, angle can be calculated in different ways: 
 * 2D, 3D or on spherical surface. Extend if necessary.
 * 
 * @author Johannes Pelzer
 */
public class AngleCalc2D
{
    /**
     * Calculate angle for a set of 3 points in a plane at point given by latTurn and lonTurn params. 
     * Angle is returned in radians.
     * The 3 points must be different from each other.
     */
    double calcAngleRad( double latPre, double lonPre, 
                        double latTurn, double lonTurn, 
                        double latNext, double lonNext) 
    {
        DistanceCalc2D dc = new DistanceCalc2D();
        
        double vectorPreLat = latPre - latTurn;
        double vectorPreLon = lonPre - lonTurn;
        double vectorPreLen = dc.calcDist(latPre, lonPre, latTurn, lonTurn);
        
        double vectorNextLat = latNext - latTurn;
        double vectorNextLon = lonNext - lonTurn;
        double vectorNextLen = dc.calcDist(latNext, lonNext, latTurn, lonTurn);
        
        if (0.0 == vectorPreLen || 0.0 == vectorNextLen ) {
            return Double.NaN;
        }
        
        double dotProduct = vectorPreLat * vectorNextLat + vectorPreLon * vectorNextLon;
        
        double angle = Math.acos(dotProduct / (vectorPreLen * vectorNextLen));
        
        return angle;
    }
    
    /**
     * Calculate angle for a set of 3 points in a plane at point given by latTurn and lonTurn params. 
     * Angle is returned in degrees.
     * The 3 points must be different from each other.
     */
    double calcAngleDeg( double latPre, double lonPre, 
                           double latTurn, double lonTurn, 
                           double latNext, double lonNext) 
    {
        return Math.toDegrees( calcAngleRad(latPre, lonPre, latTurn, lonTurn, latNext, lonNext) );
    }
    /**
     * Calculate angle between direction given by parameters and north 
     * (north by coordinates, not magnetic...)
     * 
     * @param lat1 latitude of first point
     * @param lon1 longitude of first point
     * @param lat2 latitude of next point
     * @param lon2 longitude of next point
     * @return angle between 0 and 360 degree where 0 or 360 is north and 90 is east
     */
    double calcAngleAgainstNorthDeg(double lat1, double lon1, double lat2, double lon2) 
    {
        double latNorth = lat1 + 1.0;
        double lonNorth = lon1;
        double angle = calcAngleDeg(latNorth, lonNorth, lat1, lon1, lat2, lon2);
        if (lon1 > lon2) {
            angle = 360 - angle;
        }
        return angle;
    }
}
