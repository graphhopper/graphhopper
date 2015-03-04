package com.graphhopper.reader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.graphhopper.routing.util.TurnCostEncoder;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

/**
 * Helper object which gives node cost entries for a given OSM-relation of type "restriction"
 * <p>
 * @author Karl Hübner
 */
public class OSMTurnRelation implements TurnRelation
{
    public enum Type
    {
        UNSUPPORTED, NOT, ONLY;

        private static final Map<String, Type> tags = new HashMap<String, Type>();

        static
        {
            tags.put("no_left_turn", NOT);
            tags.put("no_right_turn", NOT);
            tags.put("no_straight_on", NOT);
            tags.put("no_u_turn", NOT);
            tags.put("only_right_turn", ONLY);
            tags.put("only_left_turn", ONLY);
            tags.put("only_straight_on", ONLY);
        }

        public static Type getRestrictionType(String tag)
        {
            Type result = null;
            if (tag != null)
                result = tags.get(tag);
            return result != null ? result : UNSUPPORTED;
        }
    }

    private final long fromOsmWayId;
    private final long viaOsmNodeId;
    private final long toOsmWayId;
    private final Type restriction;

    public OSMTurnRelation(long fromWayID, long viaNodeID, long toWayID, Type restrictionType)
    {
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
    public long getOsmIdFrom()
    {
        return fromOsmWayId;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.graphhopper.reader.TurnRelation#getOsmIdTo()
     */
    @Override
    public long getOsmIdTo()
    {
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
     * @return a collection of node cost entries which can be added to the graph later
     */
    @Override
    public Collection<ITurnCostTableEntry> getRestrictionAsEntries(TurnCostEncoder encoder,
            EdgeExplorer edgeOutExplorer, EdgeExplorer edgeInExplorer, DataReader dataReader)
            {
        OSMReader osmReader = (OSMReader) dataReader;
        int nodeVia = osmReader.getInternalNodeIdOfOsmNode(this.viaOsmNodeId);

        try
        {
            // street with restriction was not included (access or tag limits etc)
            if (nodeVia == OSMReader.EMPTY)
                return Collections.emptyList();

            int edgeIdFrom = EdgeIterator.NO_EDGE;

            // get all incoming edges and receive the edge which is defined by fromOsm
            EdgeIterator iter = edgeInExplorer.setBaseNode(nodeVia);

            while (iter.next())
            {
                if (osmReader.getOsmIdOfInternalEdge(iter.getEdge()) == this.fromOsmWayId)
                {
                    edgeIdFrom = iter.getEdge();
                    break;
                }
            }

            if (edgeIdFrom == EdgeIterator.NO_EDGE)
                return Collections.emptyList();

            final Collection<ITurnCostTableEntry> entries = new ArrayList<>();
            // get all outgoing edges of the via node
            iter = edgeOutExplorer.setBaseNode(nodeVia);
            // for TYPE_ONLY_* we add ALL restrictions (from, via, * ) EXCEPT the given turn
            // for TYPE_NOT_*  we add ONE restriction  (from, via, to)
            while (iter.next())
            {
                int edgeId = iter.getEdge();
                long wayId = osmReader.getOsmIdOfInternalEdge(edgeId);
                if (edgeId != edgeIdFrom && this.restriction == Type.ONLY && wayId != this.toOsmWayId
                        || this.restriction == Type.NOT && wayId == this.toOsmWayId && wayId >= 0)
                {
                    final TurnCostTableEntry entry = new TurnCostTableEntry();
                    entry.nodeViaNode = nodeVia;
                    entry.edgeFrom = edgeIdFrom;
                    entry.edgeTo = iter.getEdge();
                    entry.flags = encoder.getTurnFlags(true, 0);
                    entries.add(entry);

                    if (this.restriction == Type.NOT)
                        break;
                }
            }
            return entries;
        } catch (Exception e)
        {
            throw new IllegalStateException("Could not built turn table entry for relation of node with osmId:" + this.viaOsmNodeId, e);
        }
            }

    @Override
    public String toString()
    {
        return "*-(" + fromOsmWayId + ")->" + viaOsmNodeId + "-(" + toOsmWayId + ")->*";
    }

    /**
     * Helper class to processing purposes only
     */
    public static class TurnCostTableEntry implements ITurnCostTableEntry
    {
        public int edgeFrom;
        public int nodeViaNode;
        public int edgeTo;
        public long flags;

        /**
         * @return an unique id (edgeFrom, edgeTo) to avoid duplicate entries if multiple encoders
         *         are involved.
         */
        @Override
        public long getItemId()
        {
            return ((long) edgeFrom) << 32 | (edgeTo);
        }

        @Override
        public int getEdgeFrom()
        {
            return edgeFrom;
        }

        @Override
        public int getEdgeTo()
        {
            return edgeTo;
        }

        @Override
        public int getVia()
        {
            return nodeViaNode;
        }

        @Override
        public long getFlags()
        {
            return flags;
        }

        @Override
        public void setFlags(long flags)
        {
            this.flags = flags;
        }

        @Override
        public String toString()
        {
            return "*-(" + edgeFrom + ")->" + nodeViaNode + "-(" + edgeTo + ")->*";
        }

        @Override
        public void setEdgeFrom(int from) {
            this.edgeFrom = from;
        }

        @Override
        public void setEdgeTo(int to) {
            this.edgeTo = to;
        }
    }

}
