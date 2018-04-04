package com.graphhopper.routing.ch;

import com.graphhopper.util.EdgeIterator;

public class AStarCHEntry extends CHEntry {
    public double weightOfVisitedPath;

    public AStarCHEntry(int edge, int incEdge, int adjNode, double heapWeight, double weightOfVisitedPath) {
        super(edge, incEdge, adjNode, heapWeight);
        this.weightOfVisitedPath = weightOfVisitedPath;
    }

    public AStarCHEntry(int node, double heapWeight, double weightOfVisitedPath) {
        this(EdgeIterator.NO_EDGE, EdgeIterator.NO_EDGE, node, heapWeight, weightOfVisitedPath);
    }

    @Override
    public AStarCHEntry getParent() {
        return (AStarCHEntry) super.getParent();
    }

    @Override
    public double getWeightOfVisitedPath() {
        return weightOfVisitedPath;
    }
}
