package com.graphhopper.routing.util;

import com.graphhopper.reader.Way;
import com.graphhopper.util.InstructionAnnotation;
import com.graphhopper.util.Translation;

/**
 * Created by sadam on 4/15/15.
 */
public class OsVehicleAvoidanceDecorator implements EncoderDecorator {
    private EncodedValue wayTypeEncoder;


    protected enum AvoidanceType
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



    }

    public int defineWayBits(int shift) {
        wayTypeEncoder = new EncodedValue("WayType", shift, 3, 1, 0, 4, true);
        shift += wayTypeEncoder.getBits();
        return shift;
    }

    public long handleWayTags(Way way, long encoded) {
        long avoidanceValue=0;

        for (AvoidanceType aType: AvoidanceType.values()) {
            if(aType.isValidForWay(way)) {
                avoidanceValue += aType.getValue();
            }
        }
        return wayTypeEncoder.setValue(encoded, avoidanceValue);
    }

    public InstructionAnnotation getAnnotation( long flags, Translation tr )
    {
        long wayType = wayTypeEncoder.getValue(flags);
        String wayName = getWayName(wayType, tr);
        return new InstructionAnnotation(0, wayName);
    }

    private String getWayName(long wayType, Translation tr) {
        String wayName="";
        for (AvoidanceType aType: AvoidanceType.values()) {
            if ((wayType & aType.getValue()) == aType.getValue()) {
                wayName += " ";
                wayName += aType.name();
            }
        }

        return wayName;
    }

}
