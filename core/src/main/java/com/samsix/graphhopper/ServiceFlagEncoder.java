package com.samsix.graphhopper;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.FootFlagEncoder;

public class ServiceFlagEncoder
    extends
        CarFlagEncoder
{
    public ServiceFlagEncoder()
    {
        super();
        
        //
        // Allow our service vehicles to take private roads
        // to get to the equipment they need to get to.
        //
        restrictedValues.remove("private");
    }

    @Override
    public String toString()
    {
        return "service";
    }
}
