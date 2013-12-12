package com.graphhopper.util;

public class Instruction
{
    public static final int TURN_SHARP_LEFT = -3;
    public static final int TURN_LEFT = -2;
    public static final int TURN_SLIGHT_LEFT = -1;
    public static final int CONTINUE_ON_STREET = 0;
    public static final int TURN_SLIGHT_RIGHT = 1;
    public static final int TURN_RIGHT = 2;
    public static final int TURN_SHARP_RIGHT = 3;
    private int indication;
    private String name;
    private double distance;
    private double time;
    private double lat;
    private double lon;

    public Instruction( int indication, String name, double distance, double time, double lat, double lon )
    {
        this.indication = indication;
        this.name = name;
        this.distance = distance;
        this.time = time;
        this.lat = lat;
        this.lon = lon;
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
     * Time in seconds until no new instruction
     */
    public double getTime()
    {
        return time;
    }

    public void setDistance( double distance )
    {
        this.distance = distance;
    }

    public void setTime( double time )
    {
        this.time = time;
    }

    /**
     * Latitude of the location where this instruction should happen.
     */
    public double getLat()
    {
        return lat;
    }

    /**
     * Longitude of the location where this instruction should happen.
     */
    public double getLon()
    {
        return lon;
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
        sb.append(time);
        sb.append(')');

        return sb.toString();
    }
}
