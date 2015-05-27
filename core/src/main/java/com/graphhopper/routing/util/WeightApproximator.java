package com.graphhopper.routing.util;

/**
 * Specifies a weight approximation between an node and the goalNode according to the specified weighting.
 * <p/>
 * @author jansoe
 */
public interface WeightApproximator
{

    /**
     * @return minimal weight fromNode to the goalNode
     */
    double approximate( int fromNode );

    void setGoalNode( int to );

    /**
     * makes a deep copy of itself
     */
    WeightApproximator duplicate();
}
