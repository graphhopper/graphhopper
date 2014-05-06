package com.samsix.graphhopper;

import com.graphhopper.routing.util.FootFlagEncoder;

public class S6FootFlagEncoder
    extends
        FootFlagEncoder
{
    public S6FootFlagEncoder()
    {
        super();
        
        restrictedValues.remove("private");
    }
}
