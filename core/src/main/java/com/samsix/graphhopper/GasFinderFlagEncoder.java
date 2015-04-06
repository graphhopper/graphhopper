package com.samsix.graphhopper;

import com.graphhopper.routing.util.FootFlagEncoder;

public class GasFinderFlagEncoder
    extends
        FootFlagEncoder
{
    public GasFinderFlagEncoder()
    {
        super();
        
        //
        // For running gas mains we don't want to avoid
        // crossing private property.
        //
        restrictedValues.remove("private");
    }

    @Override
    public String toString()
    {
        return "gasfinder";
    }
}
