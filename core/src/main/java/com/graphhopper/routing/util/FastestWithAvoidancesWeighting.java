package com.graphhopper.routing.util;

import com.graphhopper.util.EdgeIteratorState;

public class FastestWithAvoidancesWeighting extends FastestWeighting {

	private long bitMask;

	public FastestWithAvoidancesWeighting(FlagEncoder encoder, String... avoidances) {
		super(encoder);
		configureAvoidances(avoidances);
	}

	private void configureAvoidances(String[] avoidances) {
		bitMask = encoder.getBitMask(avoidances, AbstractAvoidanceDecorator.KEY);
	}
	
	@Override
    public double calcWeight( EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId )
    {
        long wayType = edge.getFlags();
        wayType = encoder.getLong(wayType, AbstractAvoidanceDecorator.KEY);
        if(bitMask!=0 && ((wayType & bitMask) > 0)) {
            return Double.POSITIVE_INFINITY;
        }
        return super.calcWeight(edge, reverse, prevOrNextEdgeId);
    }

}
