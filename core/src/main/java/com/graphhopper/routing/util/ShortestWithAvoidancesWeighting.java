package com.graphhopper.routing.util;

import com.graphhopper.util.EdgeIteratorState;

public class ShortestWithAvoidancesWeighting extends ShortestWeighting {

	private long bitMask;
	private FlagEncoder encoder;

	public ShortestWithAvoidancesWeighting(FlagEncoder encoder, String... avoidances) {
		super();
		this.encoder = encoder;
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
        
        if((wayType & bitMask) == bitMask)
            return Double.POSITIVE_INFINITY;
        return super.calcWeight(edge, reverse, prevOrNextEdgeId);
    }

}
