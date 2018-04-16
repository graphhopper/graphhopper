package com.graphhopper.Penalties;

import com.graphhopper.WeightingWithPenalties;
import com.graphhopper.util.EdgeIteratorState;

public abstract class Penalty {

    abstract public double getPenalty(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId, WeightingWithPenalties.WayData from, WeightingWithPenalties.WayData to);
}