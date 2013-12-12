package com.graphhopper.util;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TLongArrayList;
import java.util.ArrayList;
import java.util.List;

public class Instruction
{
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
    private final TDoubleArrayList distances;
    private final TLongArrayList times;
    private final PointList points;

    /**
     * The points, distances and times have exactly the same count. The last point of this
     * instruction is not duplicated here and should be in the next one. The first distance and time
     * entries are measured between the first point and the second one etc.
     */
    public Instruction( int indication, String name, TDoubleArrayList distances, TLongArrayList times, PointList pl )
    {
        this.indication = indication;
        this.name = name;
        this.distances = distances;
        this.times = times;
        this.points = pl;                

        if (distances.isEmpty())
            throw new IllegalStateException("Distances cannot be empty");

        if (times.size() != distances.size())
            throw new IllegalStateException("Distances and times must have same size");

        if (times.size() != points.size())
            throw new IllegalStateException("Points and times must have same size");
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
    public double calcDistance()
    {
        return distances.sum();
    }

    /**
     * Time in millis until no new instruction
     */
    public long calcMillis()
    {
        return times.sum();
    }

    /**
     * Latitude of the location where this instruction should take place.
     */
    public double getStartLat()
    {
        return points.getLatitude(0);
    }

    /**
     * Longitude of the location where this instruction should take place.
     */
    public double getStartLon()
    {
        return points.getLongitude(0);
    }

    /**
     * This method returns a list of gpx entries where the time (in millis) is relative to the first
     * which is 0. It does NOT contain the last point which is the first of the next instruction.
     */
    public List<GPXEntry> createGPXList()
    {
        int len = times.size();
        long sum = 0;
        List<GPXEntry> list = new ArrayList<GPXEntry>(len);
        for (int i = 0; i < len; i++)
        {
            sum += times.get(i);
            list.add(new GPXEntry(points.getLatitude(i), points.getLongitude(i), sum));
        }
        return list;
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
        sb.append(distances);
        sb.append(',');
        sb.append(times);
        sb.append(')');
        return sb.toString();
    }
}
