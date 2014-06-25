package com.samsix.graphhopper;

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
}
