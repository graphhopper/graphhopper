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

/**
 * Calculates the angle of a turn, defined by three points.
 * <p>
 * Like distance calculation, angle can be calculated in different ways: 2D, 3D or on spherical
 * surface. Extend if necessary.
 * <p>
 * @author Johannes Pelzer
 */
public class AngleCalc2D
{

    /**
     * Return orientation of line relative to north. (North by coordinates, not magnetic north)
     * <p>
     * @return Orientation in interval -pi to +pi where 0 is north and pi is south
     */
    public double calcOrientation( double lat1, double lon1, double lat2, double lon2 )
    {
        return Math.atan2(lon2 - lon1, lat2 - lat1);
    }

    /**
     * Change the representation of an orientation, so the difference to the given baseOrientation
     * will be smaller or equal to PI (180 degree). This is achieved by adding or substracting a
     * 2*PI, so the direction of the orientation will not be changed
     */
    public double alignOrientation( double baseOrientation, double orientation )
    {
        double resultOrientation;
        if (baseOrientation >= 0)
        {
            if (orientation < -Math.PI + baseOrientation)
                resultOrientation = orientation + 2 * Math.PI;
            else
                resultOrientation = orientation;

        } else
        {
            if (orientation > +Math.PI + baseOrientation)
                resultOrientation = orientation - 2 * Math.PI;
            else
                resultOrientation = orientation;
        }
        return resultOrientation;
    }

    public boolean isLeftTurn( double prevOrientation, double nextOrientation )
    {
        return (prevOrientation > nextOrientation);
    }

    /**
     * Calculate angle for a set of 3 points in a plane at point given by latTurn and lonTurn
     * params. Angle is returned in radians. The 3 points must be different from each other.
     */
    double calcTurnAngleRad( double latPre, double lonPre,
            double latTurn, double lonTurn,
            double latNext, double lonNext )
    {
        double orientationPre = calcOrientation(latPre, lonPre, latTurn, lonTurn);
        double orientationNext = calcOrientation(latTurn, lonTurn, latNext, lonNext);
        double orientationNextAligned = alignOrientation(orientationPre, orientationNext);
        return orientationNextAligned - orientationPre;
    }

    /**
     * @return Angle for a turn, where 0 is returned in case of a straight road, positive values up
     * to 180 for right turns, and negative values for left turns 180 degree is returned for a turn
     * which leads in the opposite direction
     */
    double calcTurnAngleDeg( double latPre, double lonPre,
            double latTurn, double lonTurn,
            double latNext, double lonNext )
    {
        return Math.toDegrees(calcTurnAngleRad(latPre, lonPre, latTurn, lonTurn, latNext, lonNext));
    }

    /**
     * @return orientation in interval 0 to 360 where 0 and 360 are north
     */
    double calcAzimuthDeg( double lat1, double lon1, double lat2, double lon2 )
    {
        double orientation = calcOrientation(lat1, lon1, lat2, lon2);
        double orientation0to360 = alignOrientation(Math.PI, orientation);
        return Math.toDegrees(orientation0to360);
    }

    String azimuth2compassPoint( double azimuth )
    {

        String cp;
        double slice = 360.0 / 16;
        if (azimuth < slice)
        {
            cp = "N";
        } else if (azimuth < slice * 3)
        {
            cp = "NE";
        } else if (azimuth < slice * 5)
        {
            cp = "E";
        } else if (azimuth < slice * 7)
        {
            cp = "SE";
        } else if (azimuth < slice * 9)
        {
            cp = "S";
        } else if (azimuth < slice * 11)
        {
            cp = "SW";
        } else if (azimuth < slice * 13)
        {
            cp = "W";
        } else if (azimuth < slice * 15)
        {
            cp = "NW";
        } else
        {
            cp = "N";
        }
        return cp;
    }

}
