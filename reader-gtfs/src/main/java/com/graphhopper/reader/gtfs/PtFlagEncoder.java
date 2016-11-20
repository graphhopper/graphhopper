package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodedValue;

class PtFlagEncoder extends CarFlagEncoder {

	private EncodedValue wurst;
	private EncodedValue blubb;

	PtFlagEncoder() {
		super(6, 5, 0);
		maxPossibleSpeed = 300;
	}

	@Override
	public int defineWayBits(int index, int shift) {
		shift = super.defineWayBits(index, shift);
		wurst = new EncodedValue("wurst", shift, 32, 1.0, 0, Integer.MAX_VALUE);
		shift += wurst.getBits();
		GtfsStorage.EdgeType[] edgeTypes = GtfsStorage.EdgeType.values();
		blubb = new EncodedValue("blubb", shift, 6, 1.0, GtfsStorage.EdgeType.UNSPECIFIED.ordinal(), edgeTypes[edgeTypes.length-1].ordinal());
		shift += blubb.getBits();
		return shift;
	}

	long getTime(long flags) {
        return wurst.getValue(flags);
    }

    long setTime(long flags, long time) {
        return wurst.setValue(flags, time);
    }

    GtfsStorage.EdgeType getEdgeType(long flags) {
		return GtfsStorage.EdgeType.values()[(int) blubb.getValue(flags)];
	}

	long setEdgeType(long flags, GtfsStorage.EdgeType edgeType) {
		return blubb.setValue(flags, edgeType.ordinal());
	}

	public String toString() {
		return "pt";
	}

}
