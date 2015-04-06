package com.samsix.graphhopper;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.routing.util.CarFlagEncoder;

public class TruckServiceFlagEncoder
    extends
        CarFlagEncoder
{
    public TruckServiceFlagEncoder()
    {
        super();
        
        //
        // Allow our service vehicles to take private roads
        // to get to the equipment they need to get to.
        //
        restrictedValues.remove("private");
    }
    
    
    @Override
    public long acceptWay( OSMWay way )
    {
        String hgv = way.getTag("hgv");
    
        //
        // hgv=no seems to be the way to say that trucks can't go here.
        // hgv = Heavy Goods Vehicle
        // Other values for hgv I have seen are...
        //     "local", "designated", "destination"
        //
        if ("no".equalsIgnoreCase(hgv))
        {
            return 0;
        }
        
        return super.acceptWay( way );
    }

    @Override
    public String toString()
    {
        return "truckservice";
    }
}
