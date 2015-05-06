package com.graphhopper.routing.util;

import com.graphhopper.reader.Way;
import com.graphhopper.util.InstructionAnnotation;
import com.graphhopper.util.Translation;

public abstract class AbstractAvoidanceDecorator implements EncoderDecorator {

	protected EncodedValue wayTypeEncoder;
	public static final int KEY = 303;

	protected abstract void defineEncoder(int shift);
	protected abstract EdgeAttribute[] getEdgeAttributesOfInterest();

	public int defineWayBits(int shift) {
		defineEncoder(shift);
		shift += wayTypeEncoder.getBits();
		return shift;
	}

	public InstructionAnnotation getAnnotation( long flags, Translation tr )
    {
        long wayType = wayTypeEncoder.getValue(flags);
        String wayName = getWayName(wayType, tr);
        return new InstructionAnnotation(0, wayName);
    }

	public boolean supports(int key) {
		return key == KEY;
	};
	
	@Override
	public long getLong(long flags) {
		return wayTypeEncoder.getValue(flags);
	}
	
	@Override
	public double getDouble(long flags) {
		double avoidanceType = wayTypeEncoder.getValue(flags);
		return avoidanceType;
	}

	public long handleWayTags(Way way) {
		long avoidanceValue = 0;
	
		for (EdgeAttribute aType : getEdgeAttributesOfInterest()) {
			if (aType.isValidForWay(way)) {
				avoidanceValue += aType.getValue();
			}
		}
		return wayTypeEncoder.setValue(0L, avoidanceValue);
	}

	private String getWayName(long wayType, Translation tr) {
		String wayName = "";
		for (EdgeAttribute aType : getEdgeAttributesOfInterest()) {
			if ((wayType & aType.getValue()) == aType.getValue()) {
				wayName += " ";
				wayName += aType.name();
			}
		}
	
		return wayName;
	}

	@Override
	public long getBitMask(String[] attributes) {
		long avoidanceValue = 0;
		for (EdgeAttribute aType : getEdgeAttributesOfInterest()) {
			if (aType.representedIn(attributes)) {
				avoidanceValue += aType.getValue();
			}
		}
		return avoidanceValue;
	}

}
