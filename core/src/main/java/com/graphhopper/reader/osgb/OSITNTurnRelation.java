package com.graphhopper.reader.osgb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.ITurnCostTableEntry;
import com.graphhopper.reader.OSMReader;
import com.graphhopper.reader.OSMTurnRelation.TurnCostTableEntry;
import com.graphhopper.reader.OSMTurnRelation.Type;
import com.graphhopper.reader.TurnRelation;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.TurnCostEncoder;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

/**
 * Helper object which gives OSGB ITN instruction to GraphHopper relation
 * mapping
 * <p>
 * 
 * @author Stuart Adam
 */
public class OSITNTurnRelation implements TurnRelation {
    private static final Logger logger = LoggerFactory.getLogger(OSITNTurnRelation.class);

    private static Map<String, Type> tags = new HashMap<String, Type>();

    static {
        tags.put("No Turn", Type.NOT);
        tags.put("Mandatory Turn", Type.ONLY);
    }

    public static Type getRestrictionType(String tag) {
        Type result = null;
        if (tag != null) {
            result = tags.get(tag);
        }
        return (result != null) ? result : Type.UNSUPPORTED;
    }

    private final long fromOsmWayId;
    private final long viaOsmNodeId;
    private final long toOsmWayId;
    private final Type restriction;

    public OSITNTurnRelation(long fromWayID, long viaNodeID, long toWayID, Type restrictionType) {
        this.fromOsmWayId = fromWayID;
        this.viaOsmNodeId = viaNodeID;
        this.toOsmWayId = toWayID;
        this.restriction = restrictionType;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.graphhopper.reader.TurnRelation#getOsmIdFrom()
     */
    @Override
    public long getOsmIdFrom() {
        return fromOsmWayId;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.graphhopper.reader.TurnRelation#getOsmIdTo()
     */
    @Override
    public long getOsmIdTo() {
        return toOsmWayId;
    }

    /**
     * Transforms this relation into a collection of turn cost entries
     * <p>
     * 
     * @param edgeOutExplorer
     *            an edge filter which only allows outgoing edges
     * @param edgeInExplorer
     *            an edge filter which only allows incoming edges
     * @return a collection of node cost entries which can be added to the graph
     *         later
     */
    public Collection<ITurnCostTableEntry> getRestrictionAsEntries(TurnCostEncoder encoder, EdgeExplorer edgeOutExplorer, EdgeExplorer edgeInExplorer, DataReader osmReader) {
        int viaNodeId = osmReader.getInternalNodeIdOfOsmNode(this.viaOsmNodeId);

        try {
            // street with restriction was not included (access or tag limits
            // etc)
            if (viaNodeId == OsItnReader.EMPTY)
                return Collections.emptyList();

            int edgeIdFrom = EdgeIterator.NO_EDGE;

            // get all incoming edges and receive the edge which is defined by
            // fromOsm
            EdgeIterator iter = edgeInExplorer.setBaseNode(viaNodeId);

            while (iter.next()) {
                if (osmReader.getOsmIdOfInternalEdge(iter.getEdge()) == this.fromOsmWayId) {
                    edgeIdFrom = iter.getEdge();
                    break;
                }
            }

            if (edgeIdFrom == EdgeIterator.NO_EDGE)
                return Collections.emptyList();

            final Collection<ITurnCostTableEntry> entries = new ArrayList<ITurnCostTableEntry>();
            // get all outgoing edges of the via node
            iter = edgeOutExplorer.setBaseNode(viaNodeId);
            // for TYPE_ONLY_* we add ALL restrictions (from, via, * ) EXCEPT
            // the given turn
            // for TYPE_NOT_* we add ONE restriction (from, via, to)
            while (iter.next()) {
                int edgeId = iter.getEdge();
                long wayId = osmReader.getOsmIdOfInternalEdge(edgeId);
                if (edgeId != edgeIdFrom && this.restriction == Type.ONLY && wayId != this.toOsmWayId || this.restriction == Type.NOT && wayId == this.toOsmWayId && wayId >= 0) {
                    final TurnCostTableEntry entry = new TurnCostTableEntry();
                    entry.nodeViaNode = viaNodeId;
                    entry.edgeFrom = edgeIdFrom;
                    entry.edgeTo = iter.getEdge();
                    entry.flags = encoder.getTurnFlags(true, 0);
                    entries.add(entry);

                    if (this.restriction == Type.NOT)
                        break;
                }
            }
            return entries;
        } catch (Exception e) {
            throw new IllegalStateException("Could not built turn table entry for relation of node with osmId:" + this.viaOsmNodeId, e);
        }
    }

    @Override
    public String toString() {
        return "*-(" + fromOsmWayId + ")->" + viaOsmNodeId + "-(" + toOsmWayId + ")->*";
    }

    /**
     * Helper class to processing purposes only
     */
    public static class TurnCostTableEntry implements ITurnCostTableEntry {
        public int edgeFrom;
        public int nodeViaNode;
        public int edgeTo;
        public long flags;

        /**
         * @return an unique id (edgeFrom, edgeTo) to avoid duplicate entries if
         *         multiple encoders are involved.
         */
        public long getItemId() {
            return ((long) edgeFrom) << 32 | ((long) edgeTo);
        }

        @Override
        public int getEdgeFrom() {
            return edgeFrom;
        }

        @Override
        public int getEdgeTo() {
            return edgeTo;
        }

        @Override
        public int getVia() {
            return nodeViaNode;
        }

        @Override
        public long getFlags() {
            return flags;
        }

        @Override
        public void setFlags(long flags) {
            this.flags = flags;

        }

        @Override
        public String toString() {
            return "*-(" + edgeFrom + ")->" + nodeViaNode + "-(" + edgeTo + ")->*";
        }
    }

    public long getVia() {
        return viaOsmNodeId;
    }

}
