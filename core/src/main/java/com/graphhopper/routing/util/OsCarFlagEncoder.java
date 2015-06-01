package com.graphhopper.routing.util;

import java.util.ArrayList;

public class OsCarFlagEncoder extends CarFlagEncoder {
	public OsCarFlagEncoder() {
		super(5, 5, 0);
		setOsAvoidanceDecorator();
	}

	public OsCarFlagEncoder(String propertiesStr) {
		super(propertiesStr);
		setOsAvoidanceDecorator();
	}

	public OsCarFlagEncoder(int speedBits, double speedFactor, int maxTurnCosts) {
		super(speedBits, speedFactor, maxTurnCosts);
		setOsAvoidanceDecorator();

	}

	private void setOsAvoidanceDecorator() {
		if (null == encoderDecorators) {
			encoderDecorators = new ArrayList<EncoderDecorator>(2);
		}
		encoderDecorators.add(new OsVehicleAvoidanceDecorator());
	}
	
	@Override
	public boolean supports(Class<?> feature) {
		 if (super.supports(feature))
	            return true;
		 
		 return (PriorityWithAvoidancesWeighting.class.isAssignableFrom(feature));
	}

}
