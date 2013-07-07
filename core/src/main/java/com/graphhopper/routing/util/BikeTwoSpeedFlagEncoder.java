package com.graphhopper.routing.util;

import com.graphhopper.reader.GeometryAccess;
import com.graphhopper.reader.OSMWay;
import com.graphhopper.reader.dem.ElevationAnalyzer;
import com.graphhopper.reader.RouteRelationHandler;

/**
 * Sample bike encoder that modifies speed by elevation difference.
 * Has a forward speed and a backward speed that are switched around.
 * Also prefers bicycle routes by applying a speed bonus.
 * @author Nop
 */
public class BikeTwoSpeedFlagEncoder extends BikeFlagEncoder
{
    private EncodedValue backwardSpeedEncoder;
    private ElevationAnalyzer analyzer;

    @Override
    public int queryNeeds()
    {
        return NEEDS_DEM | NEEDS_DEM_INTERPOLATION | NEEDS_BICYCLE_ROUTES;
    }

    @Override
    public int defineBits( int index, int shift )
    {
        shift = super.defineBits(index, shift);

        backwardSpeedEncoder = new EncodedValue("Reverse Speed", shift, 4, 2, HIGHWAY_SPEED.get("cycleway"), HIGHWAY_SPEED.get("primary"));
        shift += 4;

        analyzer = new ElevationAnalyzer();

        return shift;
    }


    @Override
    protected int encodeSpeed( OSMWay way, GeometryAccess geometryAccess, RouteRelationHandler routes )
    {

        analyzer.initialize(way, geometryAccess);
        analyzer.interpolate( 90 );
        analyzer.analyzeElevations();

        int speed = getSpeed(way);
        // apply a speed bonus of 15% on marked bicycle routes
        if( routes.isOnBicycleRoute( way.getId() ))
            speed = 115 * speed / 100;
        // modify speed by inclines/declines
        int forwardSpeed = adjustSpeed(speed, analyzer.getAverageIncline(), analyzer.getAscendDistance(),
                analyzer.getAverageDecline(), analyzer.getDescendDistance(), analyzer.getTotalDistance());
        int backwardSpeed = adjustSpeed(speed, -analyzer.getAverageDecline(), analyzer.getDescendDistance(),
                -analyzer.getAverageIncline(), analyzer.getAscendDistance(), analyzer.getTotalDistance());
        int encoded = speedEncoder.setValue(0, forwardSpeed);
        encoded = backwardSpeedEncoder.setValue(encoded, backwardSpeed);

        return encoded;
    }


    private int adjustSpeed( int speed, int incline, int inclineDistance, int decline, int declineDistance, int totalDistance )
    {

        if (inclineDistance == 0 && declineDistance == 0)
        {
            return speed;
        }

        // speed for level part
        int modifiedSpeed = speed * (totalDistance - inclineDistance - declineDistance);
        // speed for ascending part
        if (inclineDistance > 0)
        {
            final int upSpeed = Math.max(speed - incline, 4);
            modifiedSpeed += upSpeed * inclineDistance;
        }
        // speed for descending part
        if (declineDistance > 0)
        {
            final int downSpeed = speed + Math.min(speed / 2, speed * decline / -25);
            modifiedSpeed += downSpeed * declineDistance;
        }
        return modifiedSpeed / totalDistance;
    }

    @Override
    public int swapDirection( int flags )
    {
        flags = super.swapDirection(flags);

        return speedEncoder.swap(flags, backwardSpeedEncoder);
    }

    @Override
    public String toString()
    {
        return "BIKE3D";
    }
}
