package com.graphhopper.routing.util;

import com.graphhopper.storage.NodeAccess;

/**
 * Turns an unidirectional weight Approximation into a bidirectional consistent one.
 * <p/>
 * Ikeda, T., Hsu, M.-Y., Imai, H., Nishimura, S., Shimoura, H., Hashimoto, T., Tenmoku, K., and
 * Mitoh, K. (1994). A fast algorithm for finding better routes by ai search techniques. In VNIS,
 * pages 291–296.
 * <p/>
 *
 * @author jansoe
 */
public class ConsistentWeightApproximator {

    private NodeAccess nodeAccess;
    private Weighting weighting;
    private WeightApproximator uniDirApproximatorForward, uniDirApproximatorReverse;

    public ConsistentWeightApproximator(WeightApproximator weightApprox){
        uniDirApproximatorForward = weightApprox;
        uniDirApproximatorReverse = weightApprox.duplicate();
    }

    public void setSourceNode(int sourceNode){
        uniDirApproximatorReverse.setGoalNode(sourceNode);
    }

    public void setGoalNode(int goalNode){
        uniDirApproximatorForward.setGoalNode(goalNode);
    }

    public double approximate(int fromNode, boolean reverse)    {
        double weightApproximation = 0.5*(uniDirApproximatorForward.approximate(fromNode)
                                          - uniDirApproximatorReverse.approximate(fromNode));
        if (reverse) {
            weightApproximation *= -1;
        }

        return weightApproximation;
    }
}
