package com.graphhopper.reader.osgb;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.ITurnCostTableEntry;
import com.graphhopper.reader.OSMTurnRelation.Type;
import com.graphhopper.reader.TurnRelation;
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
	private static final Logger logger = LoggerFactory
			.getLogger(OSITNTurnRelation.class);

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
	
	 private long fromOsm;
	    private long viaOsm;
	    private long toOsm;
	    private Type restriction;

	    public OSITNTurnRelation( long fromWayID, long viaNodeID, long toWayID, Type restrictionType )
	    {
	        this.fromOsm = fromWayID;
	        this.viaOsm = viaNodeID;
	        this.toOsm = toWayID;
	        this.restriction = restrictionType;
	    }

	    public long getOsmIdFrom()
	    {
	        return fromOsm;
	    }

	    public long getOsmIdTo()
	    {
	        return toOsm;
	    }

	    /**
	     * transforms this relation into a collection of node cost entries
	     * <p>
	     * @param edgeOutExplorer an edge filter which only allows outgoing edges
	     * @param edgeInExplorer an edge filter which only allows incoming edges
	     * @return a collection of node cost entries which can be added to the graph later
	     */
	    public Collection<ITurnCostTableEntry> getRestrictionAsEntries( TurnCostEncoder encoder,
	            EdgeExplorer edgeOutExplorer, EdgeExplorer edgeInExplorer, DataReader osmReader )
	    {
	        final Set<ITurnCostTableEntry> entries = new HashSet<ITurnCostTableEntry>();

	        int viaNodeId = (int) osmReader.getInternalNodeIdOfOsmNode(this.viaOsm);

	        try
	        {
	            if (viaNodeId == OsItnReader.EMPTY)
	            {
	                throw new IllegalArgumentException("Unknown node osm id: " + this.viaOsm);
	            }

	            int edgeIdFrom = EdgeIterator.NO_EDGE;

	            // get all incoming edges and receive the edge which is defined by fromOsm
	            EdgeIterator iter = edgeInExplorer.setBaseNode(viaNodeId);

				while (iter.next())
	            {
					int edge = iter.getEdge();
					long osmIdOfInternalEdge = osmReader.getOsmIdOfInternalEdge(edge);
					logger.info("Got Edge Info: " +osmIdOfInternalEdge + " for: " + edge );
	                if (osmIdOfInternalEdge == this.fromOsm)
	                {
	                    edgeIdFrom = edge;
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
	                    	int edge = iter.getEdge();
	                    	long osmIdOfInternalEdge = osmReader.getOsmIdOfInternalEdge(edge);
	                        if (edge != edgeIdFrom && osmIdOfInternalEdge == this.toOsm)
	                        {
	                            final TurnCostTableEntry entry = new TurnCostTableEntry();
	                            entry.nodeVia = viaNodeId;
	                            entry.edgeFrom = edgeIdFrom;
	                            entry.edgeTo = edge;
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
	                    	int edge = iter.getEdge();
	    					long osmIdOfInternalEdge = osmReader.getOsmIdOfInternalEdge(edge);
	                        if (edge != edgeIdFrom && osmIdOfInternalEdge != this.toOsm)
	                        {
	                            final TurnCostTableEntry entry = new TurnCostTableEntry();
	                            entry.nodeVia = viaNodeId;
	                            entry.edgeFrom = edgeIdFrom;
	                            entry.edgeTo = edge;
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
	        logger.info("TableEntries:" + entries.size());
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

		public long getVia() {
			return viaOsm;
		}

	
}
