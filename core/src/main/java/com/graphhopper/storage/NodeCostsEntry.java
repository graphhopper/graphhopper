package com.graphhopper.storage;

/**
 * Describes a node costs entry
 * 
 * @author Karl Hübner
 */
public class NodeCostsEntry {

    private int node;
    private int edgeFrom;
    private int edgeTo;
    private double costs;

    public NodeCostsEntry() {
        costs = 0;
    }

    public int node() {
        return node;
    }

    public NodeCostsEntry node(int node) {
        this.node = node;
        return this;
    }

    public int edgeFrom() {
        return edgeFrom;
    }

    public NodeCostsEntry edgeFrom(int edgeFrom) {
        this.edgeFrom = edgeFrom;
        return this;
    }

    public int edgeTo() {
        return edgeTo;
    }

    public NodeCostsEntry edgeTo(int edgeTo) {
        this.edgeTo = edgeTo;
        return this;
    }

    public double costs() {
        return costs;
    }

    public NodeCostsEntry costs(double costs) {
        this.costs = costs;
        return this;
    }

}
