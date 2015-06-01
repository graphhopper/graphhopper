package com.graphhopper.routing.util;

import com.graphhopper.storage.AvoidanceAttributeExtension;
import com.graphhopper.util.EdgeIteratorState;

public class PriorityWithAvoidancesWeighting extends PriorityWeighting {

	private long bitMask;
	private AvoidanceAttributeExtension extension;

	public PriorityWithAvoidancesWeighting(FlagEncoder encoder, AvoidanceAttributeExtension extension, String... avoidances) {
		super(encoder);
		this.extension = extension;
		configureAvoidances(avoidances);
	}

	private void configureAvoidances(String[] avoidances) {
		bitMask = encoder.getBitMask(avoidances, AbstractAvoidanceDecorator.KEY);
	}
	
	@Override
    public double calcWeight( EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId )
    {
		try {
			long extensionPointer = edge.getAdditionalField();
			long wayType = extension.getAvoidanceFlags(extensionPointer);
			if(bitMask!=0 && ((wayType & bitMask) > 0)) {
				return Double.POSITIVE_INFINITY;
			}
		} catch (UnsupportedOperationException onse) {
			System.err.println(onse);
		}
		
        return super.calcWeight(edge, reverse, prevOrNextEdgeId);
    }

}
