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
 * @author Johannes Pelzer
 * @author Peter Karich
 */
public class AngleCalc2D
{

    /**
     * Return orientation of line relative to east.
     * <p>
     * @return Orientation in interval -pi to +pi where 0 is east
     * <p>
     * @Deprecated because it seems to be nicer to align to north so try to use calcOrientationNorth
     * instaead
     */
    public double calcOrientation( double lat1, double lon1, double lat2, double lon2 )
    {
        return Math.atan2(lat2 - lat1, lon2 - lon1);
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

    /**
     * Calculate Azimuth for a line given by two coordinates. Direction in Degree where 0 is North,
     * 90 is East, and 270 is West
     * <p>
     * @param lat1
     * @param lon1
     * @param lat2
     * @param lon2
     * @return
     */
    double calcAzimuth( double lat1, double lon1, double lat2, double lon2 )
    {
        double orientation = -calcOrientation(lat1, lon1, lat2, lon2);
        orientation += (Math.PI/2);
        
        if (orientation < 0) 
        {
            orientation += 2*Math.PI;
        }
        return Math.toDegrees(orientation);
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
