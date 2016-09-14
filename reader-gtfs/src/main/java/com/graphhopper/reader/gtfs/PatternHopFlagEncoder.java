package com.graphhopper.reader.gtfs;

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.EncodedDoubleValue;

class PatternHopFlagEncoder extends AbstractFlagEncoder {

	PatternHopFlagEncoder() {
		super(6, 5, 0);
		maxPossibleSpeed = 300;
	}

	public String toString() {
		return "pt";
	}

	@Override
	public long handleRelationTags(ReaderRelation relation, long oldRelationFlags) {
		return 0;
	}

	@Override
	public long acceptWay(ReaderWay way) {
		return 1;
	}

	@Override
	public long handleWayTags(ReaderWay way, long allowed, long relationFlags) {
		return 0;
	}

	@Override
	public int getVersion() {
		return 0;
	}

	@Override
	public boolean isBackward(long flags) {
		return false;
	}

	@Override
	public boolean isForward(long flags) {
		return true;
	}

	@Override
	public int defineWayBits(int index, int shift) {
		// first two bits are reserved for route handling in superclass
		shift = super.defineWayBits(index, shift);
		speedEncoder = new EncodedDoubleValue("Speed", shift, speedBits, speedFactor, 50,
				maxPossibleSpeed);
		return shift + speedEncoder.getBits();
	}

}
