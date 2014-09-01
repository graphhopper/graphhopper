package com.graphhopper.reader;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

        private static Map<String, Type> tags = new HashMap<String, Type>();

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

    private long fromOsm;
    private long viaOsm;
    private long toOsm;
    private Type restriction;

    public OSMTurnRelation( long fromWayID, long viaNodeID, long toWayID, Type restrictionType )
    {
        this.fromOsm = fromWayID;
        this.viaOsm = viaNodeID;
        this.toOsm = toWayID;
        this.restriction = restrictionType;
    }

    /* (non-Javadoc)
	 * @see com.graphhopper.reader.TurnRelation#getOsmIdFrom()
	 */
    @Override
	public long getOsmIdFrom()
    {
        return fromOsm;
    }

    /* (non-Javadoc)
	 * @see com.graphhopper.reader.TurnRelation#getOsmIdTo()
	 */
    @Override
	public long getOsmIdTo()
    {
        return toOsm;
    }

    /* (non-Javadoc)
	 * @see com.graphhopper.reader.TurnRelation#getRestrictionAsEntries(com.graphhopper.routing.util.TurnCostEncoder, com.graphhopper.util.EdgeExplorer, com.graphhopper.util.EdgeExplorer, com.graphhopper.reader.DataReader)
	 */
    @Override
	public Collection<ITurnCostTableEntry> getRestrictionAsEntries( TurnCostEncoder encoder,
            EdgeExplorer edgeOutExplorer, EdgeExplorer edgeInExplorer, DataReader osmReader )
    {
        final Set<ITurnCostTableEntry> entries = new HashSet<ITurnCostTableEntry>();

        int viaNodeId = osmReader.getInternalNodeIdOfOsmNode(this.viaOsm);

        try
        {
            if (viaNodeId == OSMReader.EMPTY)
            {
                throw new IllegalArgumentException("Unknown node osm id");
            }

            int edgeIdFrom = EdgeIterator.NO_EDGE;

            // get all incoming edges and receive the edge which is defined by fromOsm
            EdgeIterator iter = edgeInExplorer.setBaseNode(viaNodeId);

            while (iter.next())
            {
                if (osmReader.getOsmIdOfInternalEdge(iter.getEdge()) == this.fromOsm)
                {
                    edgeIdFrom = iter.getEdge();
                    break;
                }
            }

            //get all outgoing edges of the via node 
            iter = edgeOutExplorer.setBaseNode(viaNodeId);
            if (edgeIdFrom != EdgeIterator.NO_EDGE)
            {
                if (this.restriction == Type.NOT)
                {
                    // if we have a restriction of TYPE_NO_* we add restriction only to
                    // the given turn (from, via, to)  
                    while (iter.next())
                    {
                        if (iter.getEdge() != edgeIdFrom && osmReader.getOsmIdOfInternalEdge(iter.getEdge()) == this.toOsm)
                        {
                            final TurnCostTableEntry entry = new TurnCostTableEntry();
                            entry.nodeVia = viaNodeId;
                            entry.edgeFrom = edgeIdFrom;
                            entry.edgeTo = iter.getEdge();
                            entry.flags = encoder.getTurnFlags(true, 0);
                            entries.add(entry);
                        }
                    }

                } else if (this.restriction == Type.ONLY)
                {
                    // if we have a restriction of TYPE_ONLY_* we add restriction to
                    // any turn possibility (from, via, * ) except the given turn
                    while (iter.next())
                    {
                        if (iter.getEdge() != edgeIdFrom && osmReader.getOsmIdOfInternalEdge(iter.getEdge()) != this.toOsm)
                        {
                            final TurnCostTableEntry entry = new TurnCostTableEntry();
                            entry.nodeVia = viaNodeId;
                            entry.edgeFrom = edgeIdFrom;
                            entry.edgeTo = iter.getEdge();
                            entry.flags = encoder.getTurnFlags(true, 0);
                            entries.add(entry);
                        }
                    }
                }
            }
        } catch (Exception e)
        {
            throw new IllegalStateException("Could not built node costs table for relation of node [osmId:" + this.viaOsm + "].", e);
        }
        return entries;
    }

    /**
     * Helper class to processing purposes only
     */
    public static class TurnCostTableEntry implements ITurnCostTableEntry
    {
        public int edgeFrom;
        public int edgeTo;
        public int nodeVia;
        public long flags;

        /**
         * @return an unique id (edgeFrom, edgeTo) to avoid doubled entries during parsing
         */
        public long getItemId()
        {
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
			return nodeVia;
		}

		@Override
		public long getFlags() {
			return flags;
		}

		@Override
		public void setFlags(long flags) {
			this.flags = flags;
		}
    }

}
