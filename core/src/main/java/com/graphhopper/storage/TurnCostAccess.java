package com.graphhopper.storage;

import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.EncodingManager;

import static com.graphhopper.routing.util.EncodingManager.getKey;
import static com.graphhopper.routing.util.parsers.OSMTurnRelationParser.EV_SUFFIX;

/**
 * A stateful possibility to access turn cost of the TurnCostExtension. Reuse for optimal speed. Use one per thread.
 */
public class TurnCostAccess {
    private final IntsRef EMPTY;
    private final TurnCostExtension turnCostExtension;
    private final IntsRef tcFlags;
    private final DecimalEncodedValue turnCostEnc;

    public TurnCostAccess(String name, GraphHopperStorage graph) {
        this(name, (TurnCostExtension) graph.getExtension(), graph.getEncodingManager());
    }

    public TurnCostAccess(String name, Graph graph, EncodingManager encodingManager) {
        this(name, (TurnCostExtension) graph.getExtension(), encodingManager);
    }

    public TurnCostAccess(String name, TurnCostExtension extension, EncodingManager encodingManager) {
        this.turnCostExtension = extension;
        tcFlags = encodingManager.createTurnCostFlags();
        EMPTY = new IntsRef(tcFlags.length);
        turnCostEnc = encodingManager.getDecimalEncodedValue(getKey(name, EV_SUFFIX));
    }

    /**
     * @return the turn cost of the viaNode when going from "fromEdge" to "toEdge"
     */
    public double get(int fromEdge, int viaNode, int toEdge) {
        turnCostExtension.readFlags(tcFlags, fromEdge, viaNode, toEdge);
        return turnCostEnc.getDecimal(false, tcFlags);
    }

    /**
     * Adds the turn cost to the viaNode when going from "fromEdge" to "toEdge"
     */
    public TurnCostAccess add(int fromEdge, int viaNode, int toEdge, double cost) {
        if (Double.isInfinite(cost))
            throw new IllegalArgumentException("For infinity use addRestriction");

        turnCostEnc.setDecimal(false, tcFlags, cost);
        turnCostExtension.addTurnCost(tcFlags, fromEdge, viaNode, toEdge);
        return this;
    }

    public TurnCostAccess addRestriction(int fromEdge, int viaNode, int toEdge) {
        // reset is required as we could have read a value for other vehicles before (that was changed in the meantime) that we would overwrite
        tcFlags.ints[0] = 0;
        turnCostEnc.setDecimal(false, tcFlags, Double.POSITIVE_INFINITY);
        turnCostExtension.addTurnCost(tcFlags, fromEdge, viaNode, toEdge);
        return this;
    }

    TurnCostAccess clear(int fromEdge, int viaNode, int toEdge) {
        // clears only this vehicle although all bits are 0 for EMPTY
        turnCostExtension.mergeOrOverwriteTurnInfo(EMPTY, fromEdge, viaNode, toEdge, false);
        return this;
    }

    // TODO NOW instead of this directly use graph.getExtension for TurnCostExtension
    public TurnCostExtension getTurnCostExtension() {
        return turnCostExtension;
    }
}
