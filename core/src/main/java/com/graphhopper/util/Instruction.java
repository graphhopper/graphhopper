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
import java.util.Map;
import java.util.HashMap;
import java.text.DecimalFormat;

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
    private final int sign;
    private final String name;
    private double distance;
    private long time;
    final PointList points;
    private final int pavementType;
    private final int waytype;

    /**
     * The points, distances and times have exactly the same count. The last point of this
     * instruction is not duplicated here and should be in the next one.
     */
    public Instruction( int sign, String name, int waytype, int pavementType, PointList pl )
    {
        this.sign = sign;
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

    public int getSign()
    {
        return sign;
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

    public Instruction setTime( long time )
    {
        this.time = time;
        return this;
    }

    /**
     * Time in time until no new instruction
     */
    public long getTime()
    {
        return time;
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

    public PointList getPoints()
    {
        return points;
    }

    /**
     * This method returns a list of gpx entries where the time (in time) is relative to the first
     * which is 0. It does NOT contain the last point which is the first of the next instruction.
     * <p>
     * @return the time offset to add for the next instruction
     */
    long fillGPXList( List<GPXEntry> list, long time,
            Instruction prevInstr, Instruction nextInstr, boolean firstInstr )
    {
        checkOne();
        int len = points.size();
        long prevTime = time;
        double lat = points.getLatitude(0);
        double lon = points.getLongitude(0);
        for (int i = 0; i < len; i++)
        {
            boolean last = i + 1 == len;
            double nextLat = last ? nextInstr.getFirstLat() : points.getLatitude(i + 1);
            double nextLon = last ? nextInstr.getFirstLon() : points.getLongitude(i + 1);

            list.add(new GPXEntry(lat, lon, prevTime));
            // TODO in the case of elevation data the air-line distance is probably not precise enough
            prevTime = Math.round(prevTime + this.time * distanceCalc.calcDist(nextLat, nextLon, lat, lon) / distance);
            lat = nextLat;
            lon = nextLon;
        }
        return time + this.time;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        sb.append(sign);
        sb.append(',');
        sb.append(name);
        sb.append(',');
        sb.append(distance);
        sb.append(',');
        sb.append(time);
        sb.append(')');
        return sb.toString();
    }

    public Map<String, String> calcExtensions()
    {
        AngleCalc2D ac = new AngleCalc2D();
        DecimalFormat angleFormatter = new DecimalFormat("#");

        // Add info for extensions
        Map<String, String> extensions = new HashMap<String, String>();

        // TODO instead of indication use angle which can be used here
//        double distanceToNext = distanceCalc.calcDist(nextLat, nextLon, lat, lon);
//        extensions.put("distance", angleFormatter.format(distanceToNext));
//
//        if (!(firstInstr && first))
//        {
//            // impossible to calculate an angle for first point of first instruction                
//            double prevLat = first ? prevInstr.getLastLat() : points.getLatitude(i - 1);
//            double prevLon = first ? prevInstr.getLastLon() : points.getLongitude(i - 1);
//            double turnAngle = ac.calcTurnAngleDeg(prevLat, prevLon, lat, lon, nextLat, nextLon);
//            extensions.put("turn-angle", angleFormatter.format(turnAngle));
//        }
//
//        double azimuth = ac.calcAzimuthDeg(lat, lon, nextLat, nextLon);
//        extensions.put("azimuth", angleFormatter.format(azimuth));
//        extensions.put("direction", ac.azimuth2compassPoint(azimuth));
        return extensions;
    }

    void checkOne()
    {
        if (points.size() < 1)
            throw new IllegalStateException("Instruction must contain at least one point " + toString());
    }
}
