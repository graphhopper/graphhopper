package com.graphhopper.routing.util;

import com.graphhopper.storage.AvoidanceAttributeExtension;
import com.graphhopper.util.EdgeIteratorState;

public class ShortestWithAvoidancesWeighting extends ShortestWeighting {

	private long bitMask;
	private FlagEncoder encoder;
	private AvoidanceAttributeExtension extension;

	public ShortestWithAvoidancesWeighting(FlagEncoder encoder, AvoidanceAttributeExtension extension, String... avoidances) {
		super();
		this.extension = extension;
		this.encoder = encoder;
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
