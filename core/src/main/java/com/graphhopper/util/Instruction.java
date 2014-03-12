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
    private final int indication;
    private final String name;
    private double distance;
    private long millis;
    final PointList points;
    private final int pavementType;
    private final int waytype;

    /**
     * The points, distances and times have exactly the same count. The last point of this
     * instruction is not duplicated here and should be in the next one.
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
    long fillGPXList( List<GPXEntry> list, long time, 
            Instruction prevInstr, Instruction nextInstr, boolean firstInstr )
    {
        DistanceCalc dc = new DistanceCalc2D();
        AngleCalc2D ac = new AngleCalc2D();
        DecimalFormat angleFormatter = new DecimalFormat("#");
        
        checkOne();
        int len = points.size();
        long prevTime = time;
        double lat = points.getLatitude(0);
        double lon = points.getLongitude(0);
        for (int i = 0; i < len; i++)
        {
            boolean first = i == 0;
            boolean last = i + 1 == len;
            double nextLat = last ? nextInstr.getFirstLat() : points.getLatitude(i + 1);
            double nextLon = last ? nextInstr.getFirstLon() : points.getLongitude(i + 1);

            // Add info for extensions
            Map<String, String> extensions = new HashMap<String, String>();
            double distanceToNext = distanceCalc.calcDist(nextLat, nextLon, lat, lon);
            extensions.put("distance", angleFormatter.format(distanceToNext));
                        
            if (!(firstInstr && first)) {   // impossible to calculate an angle for first point of first instruction
                double turnAngle = 180;
                double prevLat = first ? prevInstr.getLastLat() : points.getLatitude(i - 1);
                double prevLon = first ? prevInstr.getLastLon() : points.getLongitude(i - 1);
                turnAngle = ac.calcTurnAngleDeg(prevLat, prevLon, lat, lon, nextLat, nextLon);
                extensions.put("turn-angle", angleFormatter.format(turnAngle));
            }
            
            double azimuth = ac.calcAngleAgainstNorthDeg(lat, lon, nextLat, nextLon);
            extensions.put("azimuth", angleFormatter.format(azimuth));
            extensions.put("direction", azimuth2compassPoint(azimuth));
        
            
            list.add(new GPXEntry(lat, lon, prevTime, extensions));
            // TODO in the case of elevation data the air-line distance is probably not precise enough
            prevTime = Math.round(prevTime + millis * distanceCalc.calcDist(nextLat, nextLon, lat, lon) / distance);
            lat = nextLat;
            lon = nextLon;
        }
        return time + millis;
    }
    
    private String azimuth2compassPoint(double azimuth) {
        
        String cp = "N";
        double slice = 360 / 16;
        
        if (azimuth > 0 && azimuth <= slice ) {
            cp = "N";
        } else if (azimuth > slice && azimuth <= slice * 3 ) {
            cp = "NE";
        } else if (azimuth > slice * 3 && azimuth <= slice * 5 ) {
            cp = "E";
        } else if (azimuth > slice * 5 && azimuth <= slice * 7 ) {
            cp = "SE";
        } else if (azimuth > slice * 7 && azimuth <= slice * 9 ) {
            cp = "S";
        } else if (azimuth > slice * 9 && azimuth <= slice * 11 ) {
            cp = "SW";
        } else if (azimuth > slice * 11 && azimuth <= slice * 13 ) {
            cp = "W";
        } else if (azimuth > slice * 13 && azimuth <= slice * 15 ) {
            cp = "NW";
        }
        return cp;
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

    void checkOne()
    {
        if (points.size() < 1)
            throw new IllegalStateException("Instruction must contain at least one point " + toString());
    }
}
