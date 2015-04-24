package com.graphhopper.routing.util;

import com.graphhopper.reader.Way;

/**
 * Created by sadam on 4/15/15.
 */
public class OsVehicleAvoidanceDecorator extends AbstractAvoidanceDecorator {
    protected enum AvoidanceType implements EdgeAttribute
    {
        MOTORWAYS(1) {
            @Override
            public boolean isValidForWay(Way way) {
               return way.hasTag("highway", "Motorway", "motorway");
            }
        },
        TOLL(2) {
            @Override
            public boolean isValidForWay(Way way) {
                return way.hasTag("toll", "yes");
            }
        };


        private final long value;

        private AvoidanceType( long value )
        {
            this.value = value;
        }

        public long getValue()
        {
            return value;
        }

        public boolean isValidForWay(Way way) {
            return false;
        }
        
        public boolean representedIn(String[] attributes) {
			for (String attribute : attributes) {
				if(attribute.equals(this.toString())) {
					return true;
				}
			}
			return false;
		}
    }

    @Override
	protected void defineEncoder(int shift) {
		wayTypeEncoder = new EncodedValue("HazardType", shift, 3, 1, 0, 4, true);
	}
    
    protected EdgeAttribute[] getEdgeAttributesOfInterest() {
		return AvoidanceType.values();
	}

}
