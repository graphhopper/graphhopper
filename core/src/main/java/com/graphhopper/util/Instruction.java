package com.graphhopper.util;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TLongArrayList;
import java.util.List;

public class Instruction
{
    private static DistanceCalc distanceCalc = new DistanceCalcEarth();
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
    private final double distance;
    private final long millis;
    private final PointList points;

    /**
     * The points, distances and times have exactly the same count. The last point of this
     * instruction is not duplicated here and should be in the next one. The first distance and time
     * entries are measured between the first point and the second one etc.
     */
    public Instruction( int indication, String name, double distance, long millis, PointList pl )
    {
        this.indication = indication;
        this.name = name;
        this.distance = distance;
        if (Double.isNaN(distance))
            throw new IllegalStateException("distance of Instruction cannot be empty! " + toString());
        this.millis = millis;
        this.points = pl;
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

    /**
     * Distance in meter until no new instruction
     */
    public double getDistance()
    {
        return distance;
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
            if (!Double.isNaN(prevLat)) {
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
