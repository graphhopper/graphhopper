package com.graphhopper.routing.ch;

public class SmartWitnessSearchEntry extends CHEntry {
    boolean onOrigPath;
    boolean viaCenter;

    public SmartWitnessSearchEntry(int edge, int incEdge, int adjNode, double weight, boolean onOrigPath, boolean viaCenter) {
        super(edge, incEdge, adjNode, weight);
        this.onOrigPath = onOrigPath;
        this.viaCenter = viaCenter;
    }

    public SmartWitnessSearchEntry getParent() {
        return (SmartWitnessSearchEntry) super.parent;
    }

    @Override
    public String toString() {
        return super.toString() + ", onOrigPath: " + onOrigPath + ", viaCenter: " + viaCenter;
    }
}
