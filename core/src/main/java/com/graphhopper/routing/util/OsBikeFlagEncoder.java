package com.graphhopper.routing.util;

import java.util.ArrayList;

public class OsBikeFlagEncoder extends BikeFlagEncoder {
	
		public OsBikeFlagEncoder() {
	        super(4, 2, 0);
	        setOsAvoidanceDecorator();
	    }

	    public OsBikeFlagEncoder( String propertiesStr )
	    {
	    	super(propertiesStr);
	    	setOsAvoidanceDecorator();
	    }

	    public OsBikeFlagEncoder( int speedBits, double speedFactor, int maxTurnCosts )
	    {
	    	super(speedBits, speedFactor, maxTurnCosts);
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
