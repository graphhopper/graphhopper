package com.graphhopper.routing.util;

/**
 * Specifies a weight approximation between an node and the goalNode according to the specified weighting.
 * <p/>
 * @author Jan Soe
 */
public interface WeightApproximator
{

    /**
     * @return minimal weight fromNode to the toNode
     */
    double approximate(int fromNode, int toNode);

}
