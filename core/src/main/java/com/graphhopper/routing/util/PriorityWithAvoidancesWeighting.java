package com.graphhopper.routing.util;

import com.graphhopper.util.EdgeIteratorState;

public class PriorityWithAvoidancesWeighting extends PriorityWeighting {

	private long bitMask;

	public PriorityWithAvoidancesWeighting(FlagEncoder encoder, String... avoidances) {
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
        System.err.println("BITMASK:" + bitMask + " BITWEIGHT:" + wayType);
        
        if(bitMask!=0 && ((wayType & bitMask) == bitMask))
            return Double.POSITIVE_INFINITY;
        return super.calcWeight(edge, reverse, prevOrNextEdgeId);
    }

}
