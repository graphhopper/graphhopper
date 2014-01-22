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

import java.util.List;

public class Instruction
{
    private static final DistanceCalc distanceCalc = new DistanceCalcEarth();
    public static final int TURN_SHARP_LEFT = -3;
    public static final int TURN_LEFT = -2;
    public static final int TURN_SLIGHT_LEFT = -1;
    public static final int CONTINUE_ON_STREET = 0;
    public static final int TURN_SLIGHT_RIGHT = 1;
    public static final int TURN_RIGHT = 2;
    public static final int TURN_SHARP_RIGHT = 3;
    public static final int FINISH = 4;
    private final int indication;
    private final String name;
    private double distance;
    private long millis;
    private final PointList points;
    private final int pavementType;
    private final int waytype;

    /**
     * The points, distances and times have exactly the same count. The last point of this
     * instruction is not duplicated here and should be in the next one. The first distance and time
     * entries are measured between the first point and the second one etc.
     */
    public Instruction( int indication, String name, int waytype, int pavementType, PointList pl )
    {
        this.indication = indication;
        this.name = name;
        this.points = pl;
        this.waytype = waytype;
        this.pavementType = pavementType;
    }

    public int getPavement()
    {
        return pavementType;
    }

    public int getWayType()
    {
        return waytype;
    }

    public int getIndication()
    {
        return indication;
    }

    /**
     * The instruction for the person/driver to execute.
     */
    public String getName()
    {
        return name;
    }

    public Instruction setDistance( double distance )
    {
        this.distance = distance;
        return this;
    }

    /**
     * Distance in meter until no new instruction
     */
    public double getDistance()
    {
        return distance;
    }

    public Instruction setMillis( long millis )
    {
        this.millis = millis;
        return this;
    }
    
    /**
     * Time in millis until no new instruction
     */
    public long getMillis()
    {
        return millis;
    }

    /**
     * Latitude of the location where this instruction should take place.
     */
    double getFirstLat()
    {
        return points.getLatitude(0);
    }

    /**
     * Longitude of the location where this instruction should take place.
     */
    double getFirstLon()
    {
        return points.getLongitude(0);
    }

    double getLastLat()
    {
        return points.getLatitude(points.size() - 1);
    }

    double getLastLon()
    {
        return points.getLongitude(points.size() - 1);
    }

    /**
     * This method returns a list of gpx entries where the time (in millis) is relative to the first
     * which is 0. It does NOT contain the last point which is the first of the next instruction.
     * <p>
     * @return the time offset to add for the next instruction
     */
    public long fillGPXList( List<GPXEntry> list, long time, double prevFactor, double prevLat, double prevLon )
    {
        int len = points.size();
        for (int i = 0; i < len; i++)
        {
            double lat = points.getLatitude(i);
            double lon = points.getLongitude(i);
            if (!Double.isNaN(prevLat))
            {
                // Here we assume that the same speed is used until the next instruction.
                // If we would calculate all the distances (and times) up front there
                // would be a problem where the air-line distance is not the distance returned from the edge
                // e.g. in the case if we include elevation data                
                time += distanceCalc.calcDist(prevLat, prevLon, lat, lon) / prevFactor;
            }
            list.add(new GPXEntry(lat, lon, time));
            prevFactor = distance / millis;
            prevLat = lat;
            prevLon = lon;
        }
        return time;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        sb.append(indication);
        sb.append(',');
        sb.append(name);
        sb.append(',');
        sb.append(distance);
        sb.append(',');
        sb.append(millis);
        sb.append(')');
        return sb.toString();
    }
}
