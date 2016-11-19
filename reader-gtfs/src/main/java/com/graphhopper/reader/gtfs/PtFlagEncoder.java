package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodedValue;

class PtFlagEncoder extends CarFlagEncoder {

	private EncodedValue wurst;

	PtFlagEncoder() {
		super(6, 5, 0);
		maxPossibleSpeed = 300;
	}

	@Override
	public int defineWayBits(int index, int shift) {
		shift = super.defineWayBits(index, shift);
		wurst = new EncodedValue("wurst", shift, 32, 1.0, 0, Integer.MAX_VALUE);
        shift += wurst.getBits();
        return shift;
	}

	long getTime(long flags) {
        return wurst.getValue(flags);
    }

    long setTime(long flags, long time) {
        return wurst.setValue(flags, time);
    }

	public String toString() {
		return "pt";
	}

}
