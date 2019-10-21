package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.Helper;

import java.util.List;

import static com.graphhopper.routing.util.EncodingManager.getKey;

// TODO NOW define interface from class
public class OSMTurnCostParser {
    private String name;
    private IntEncodedValue turnCostEnc;
    private EdgeExplorer edgeInExplorer;
    private EdgeExplorer edgeOutExplorer;
    private final int maxTurnCosts;
    private final BooleanEncodedValue accessEnc;

    /**
     * @param maxTurnCosts specify the maximum value used for turn costs, if this value is reached a
     *                     turn is forbidden and results in costs of positive infinity.
     */
    public OSMTurnCostParser(String name, EncodedValueLookup lookup, int maxTurnCosts) {
        this.name = name;
        this.maxTurnCosts = maxTurnCosts;
        accessEnc = lookup.getEncodedValue(getKey(name, "access"), BooleanEncodedValue.class);
    }

    public EdgeExplorer createEdgeOutExplorer(Graph graph) {
        if (edgeOutExplorer == null)
            edgeOutExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.outEdges(accessEnc));
        return edgeOutExplorer;
    }

    public EdgeExplorer createEdgeInExplorer(Graph graph) {
        if (edgeInExplorer == null)
            edgeInExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.inEdges(accessEnc));
        return edgeInExplorer;
    }

    public void createRelationEncodedValues(List<EncodedValue> list) {
        int turnBits = Helper.countBitValue(maxTurnCosts);
        // TODO NOW the old code did: override to avoid expensive Math.round
        list.add(turnCostEnc = new UnsignedDecimalEncodedValue(getKey(name, "turn_cost"), turnBits, 1, 0, false, true));
    }

    /**
     * Helper class to processing purposes only
     */
    public static class TurnCostTableEntry {
        public int edgeFrom;
        public int nodeVia;
        public int edgeTo;
        long flags;

        /**
         * @return an unique id (edgeFrom, edgeTo) to avoid duplicate entries if multiple encoders
         * are involved.
         */
        public long getItemId() {
            return ((long) edgeFrom) << 32 | ((long) edgeTo);
        }

        @Override
        public String toString() {
            return "*-(" + edgeFrom + ")->" + nodeVia + "-(" + edgeTo + ")->*";
        }
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }
}
