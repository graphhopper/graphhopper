package com.samsix.graphhopper;

import com.graphhopper.routing.util.FootFlagEncoder;

public class S6FootFlagEncoder
    extends
        FootFlagEncoder
{
    /**
     * We could make this a specific GasFinderFlagEncoder
     * due to the private property crossing. BUT I want
     * to have a general "FOOT" flag encoder as well since
     * our RouteFinder has a TripMode=WALKING that I need
     * a FootFlagEncoder for. So, we might as well just use
     * this one. If we *ever* need to have a walking route
     * (for whatever reason) that shouldn't cross private
     * territory then we can change it then. We would have
     * to refactor our RouteFinder to either have some extra
     * parameters or add different TripModes.
     */
    public S6FootFlagEncoder()
    {
        super();
        
        //
        // For running gas mains we don't want to avoid
        // crossing private property.
        //
        restrictedValues.remove("private");
    }
}
