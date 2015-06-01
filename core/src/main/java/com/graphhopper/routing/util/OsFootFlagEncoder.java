package com.graphhopper.routing.util;

import java.util.ArrayList;

public class OsFootFlagEncoder extends FootFlagEncoder {
	public OsFootFlagEncoder() {
        super(4, 1);
        setOsAvoidanceDecorator();
    }

    public OsFootFlagEncoder( String propertiesStr )
    {
    	super(propertiesStr);
    	setOsAvoidanceDecorator();
    }

    public OsFootFlagEncoder( int speedBits, double speedFactor, int maxTurnCosts )
    {
    	super(speedBits, speedFactor);
    	 setOsAvoidanceDecorator();
    	
    }

	private void setOsAvoidanceDecorator() {
		if(null==encoderDecorators) {
			encoderDecorators = new ArrayList<EncoderDecorator>(2);
		}
		encoderDecorators.add(new OsAvoidanceDecorator());
	}
	
	@Override
	public boolean supports(Class<?> feature) {
		 if (super.supports(feature))
	            return true;
		 
		 return (PriorityWithAvoidancesWeighting.class.isAssignableFrom(feature));
	}

}
