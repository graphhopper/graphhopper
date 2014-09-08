package com.graphhopper.routing.util.osgb;

import com.graphhopper.reader.Relation;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodedValue;

public class RelationCarFlagEncoder extends CarFlagEncoder {

	private EncodedValue relationCodeEncoder;

	@Override
	public long handleRelationTags(Relation relation, long oldRelationFlags) {
		oldRelationFlags = super.handleRelationTags(relation, oldRelationFlags);

//		System.err.println(relation.getTag("oneway"));
		boolean isRoundabout = relation.hasTag("junction", "roundabout");
		long code = 0;
		if (isRoundabout)
			code = setBool(code, K_ROUNDABOUT, true);
		if (relation.hasTag("oneway", oneways) || isRoundabout) {
//			System.err.println("ONE WAY:" + relation.getTag("oneway"));
			if (relation.hasTag("oneway", "-1"))
				code |= backwardBit;
			else
				code |= forwardBit;
		}
		int oldCode = (int) relationCodeEncoder.getValue(oldRelationFlags);
		if (oldCode < code)
			return relationCodeEncoder.setValue(0, code);
		return oldRelationFlags;
	}

	@Override
	public int defineRelationBits(int index, int shift) {
		relationCodeEncoder = new EncodedValue("RelationCode", shift, 3, 1, 0,
				7);
		return shift + relationCodeEncoder.getBits();
	}
}
