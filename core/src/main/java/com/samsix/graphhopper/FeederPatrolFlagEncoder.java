package com.samsix.graphhopper;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.FootFlagEncoder;

public class FeederPatrolFlagEncoder
    extends
        CarFlagEncoder
{
    public FeederPatrolFlagEncoder()
    {
        super();
        
        restrictedValues.remove("private");
    }
    
    
    @Override
    public long acceptWay( OSMWay way )
    {
        //
        // Not allowing highway travel for feeder patrols
        // so that the patrol drivers are not sent on the
        // highway to inspect the network.
        //
        String highwayValue = way.getTag("highway");

        if ("motorway".equals( highwayValue )
            || "motorway_link".equals( highwayValue ) )
        {
            return 0;
        }
        
        return super.acceptWay( way );
    }

    @Override
    public String toString()
    {
        return "feederpatrol";
    }
}
