package com.graphhopper.routing.util;

/**
 * Describes a node costs entry
 * 
 * @author Karl Hübner
 */
public class TurnCostsEntry {

    private int node;
    private int edgeFrom;
    private int edgeTo;
    private int flags;

    public TurnCostsEntry() {
        flags = 0;
    }

    public int node() {
        return node;
    }

    public TurnCostsEntry node(int node) {
        this.node = node;
        return this;
    }

    public int edgeFrom() {
        return edgeFrom;
    }

    public TurnCostsEntry edgeFrom(int edgeFrom) {
        this.edgeFrom = edgeFrom;
        return this;
    }

    public int edgeTo() {
        return edgeTo;
    }

    public TurnCostsEntry edgeTo(int edgeTo) {
        this.edgeTo = edgeTo;
        return this;
    }

    public int flags() {
        return flags;
    }

    public TurnCostsEntry flags(int flags) {
        this.flags = flags;
        return this;
    }

}
