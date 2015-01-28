package com.samsix.graphhopper;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.FootFlagEncoder;

public class S6CarFlagEncoder
    extends
        CarFlagEncoder
{
    public S6CarFlagEncoder()
    {
        super();
        
        restrictedValues.remove("private");
    }
    
    
    @Override
    public long acceptWay( OSMWay way )
    {
        String highwayValue = way.getTag("highway");

        //
        // Not allowing highway travel. This was done for
        // the FeederPatrol, but I think is OK for all of our
        // needs. Anyway, putting this here for now and we may have
        // to change this later. Maybe we have to keep two different
        // datasets? One that allows highways and one that doesn't?
        // I wish we could set this stuff
        // at request time.
        //
        if ("motorway".equals( highwayValue )
            || "motorway_link".equals( highwayValue ) )
        {
            return 0;
        }
        
        return super.acceptWay( way );
    }
}
