package com.samsix.graphhopper;

import java.util.ArrayList;
import java.util.List;

import com.graphhopper.routing.util.BikeFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;

public class S6GHUtils
{
    private S6GHUtils()
    {
        // prevent instantiation
    }
    
    
    public static EncodingManager getS6EncodingManager()
    {
        List<FlagEncoder> encoders = new ArrayList<FlagEncoder>();
        encoders.add(new S6FootFlagEncoder());
        encoders.add(new NoHighwayFlagEncoder());
        encoders.add(new S6CarFlagEncoder());
        encoders.add(new TruckFlagEncoder());
        encoders.add(new BikeFlagEncoder());
        
        return new EncodingManager(encoders, 8);
    }
}
