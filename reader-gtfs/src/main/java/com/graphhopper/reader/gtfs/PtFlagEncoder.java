package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.EncodedValue;
import com.graphhopper.routing.util.FootFlagEncoder;

class PtFlagEncoder extends FootFlagEncoder {

	private EncodedValue time;
	private EncodedValue transfers;
	private EncodedValue validityId;
	private EncodedValue type;

	PtFlagEncoder() {
		super(6, 5);
		maxPossibleSpeed = 300;
	}

	@Override
	public int defineWayBits(int index, int shift) {
		shift = super.defineWayBits(index, shift);
		time = new EncodedValue("time", shift, 32, 1.0, 0, Integer.MAX_VALUE);
		shift += time.getBits();
		transfers = new EncodedValue("transfers", shift, 1, 1.0, 0, 1);
		shift += transfers.getBits();
		validityId = new EncodedValue("validityId", shift, 11, 1.0, 0, 2047);
		shift += validityId.getBits();
		GtfsStorage.EdgeType[] edgeTypes = GtfsStorage.EdgeType.values();
		type = new EncodedValue("type", shift, 6, 1.0, GtfsStorage.EdgeType.UNSPECIFIED.ordinal(), edgeTypes[edgeTypes.length-1].ordinal());
		shift += type.getBits();
		return shift;
	}

	long getTime(long flags) {
        return time.getValue(flags);
    }

    long setTime(long flags, long time) {
        return this.time.setValue(flags, time);
    }

    int getTransfers(long flags) {
		return (int) transfers.getValue(flags);
	}

	long setTransfers(long flags, int transfers) {
		return this.transfers.setValue(flags, transfers);
	}

	int getValidityId(long flags) {
		return (int) validityId.getValue(flags);
	}

	long setValidityId(long flags, int validityId) {
		return this.validityId.setValue(flags, validityId);
	}

	GtfsStorage.EdgeType getEdgeType(long flags) {
		return GtfsStorage.EdgeType.values()[(int) type.getValue(flags)];
	}

	long setEdgeType(long flags, GtfsStorage.EdgeType edgeType) {
		return type.setValue(flags, edgeType.ordinal());
	}

	public String toString() {
		return "pt";
	}

}
