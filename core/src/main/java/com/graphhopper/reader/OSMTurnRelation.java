package com.graphhopper.reader;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.graphhopper.routing.util.TurnCostEncoder;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

import java.util.*;

/**
 * Helper object which gives node cost entries for a given OSM-relation of type "restriction"
 * <p/>
 * @author Karl Hübner
 */
public class OSMTurnRelation
{
    enum Type
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

        public static Type getRestrictionType( String tag )
        {
            Type result = null;
            if (tag != null)
            {
                result = tags.get(tag);
            }
            return (result != null) ? result : UNSUPPORTED;
        }
    }

    private final long fromOsmWayId;
    private final long viaOsmNodeId;
    private final long toOsmWayId;
    private final Type restriction;

    OSMTurnRelation( long fromWayID, long viaNodeID, long toWayID, Type restrictionType )
    {
        this.fromOsmWayId = fromWayID;
        this.viaOsmNodeId = viaNodeID;
        this.toOsmWayId = toWayID;
        this.restriction = restrictionType;
    }

    long getOsmIdFrom()
    {
        return fromOsmWayId;
    }

    long getOsmIdTo()
    {
        return toOsmWayId;
    }

    /**
     * Transforms this relation into a collection of turn cost entries
     * <p/>
     * @param edgeOutExplorer an edge filter which only allows outgoing edges
     * @param edgeInExplorer an edge filter which only allows incoming edges
     * @return a collection of node cost entries which can be added to the graph later
     */
    public Collection<TurnCostTableEntry> getRestrictionAsEntries( TurnCostEncoder encoder,
                                                                   EdgeExplorer edgeOutExplorer, EdgeExplorer edgeInExplorer, OSMReader osmReader )
    {
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

            final Collection<TurnCostTableEntry> entries = new ArrayList<TurnCostTableEntry>();
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
                    entry.nodeVia = nodeVia;
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
    public static class TurnCostTableEntry
    {
        public int edgeFrom;
        public int nodeVia;
        public int edgeTo;
        public long flags;

        /**
         * @return an unique id (edgeFrom, edgeTo) to avoid duplicate entries if multiple encoders
         * are involved.
         */
        public long getItemId()
        {
            return ((long) edgeFrom) << 32 | ((long) edgeTo);
        }

        @Override
        public String toString()
        {
            return "*-(" + edgeFrom + ")->" + nodeVia + "-(" + edgeTo + ")->*";
        }
    }

}
