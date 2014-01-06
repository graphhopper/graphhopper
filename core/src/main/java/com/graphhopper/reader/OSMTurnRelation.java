package com.graphhopper.reader;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.graphhopper.reader.OSMRelationFactory.OSMRelationFactoryEngine;
import com.graphhopper.routing.util.TurnCostEncoder;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

/**
 * Helper object which gives node cost entries for a given OSM-relation of type "restriction"
 * <p>
 * @author Karl Hübner
 */
public class OSMTurnRelation extends OSMRelation
{

    public static final int TYPE_UNSUPPORTED = 0;
    public static final int TYPE_NO_LEFT_TURN = 1;
    public static final int TYPE_NO_RIGHT_TURN = 2;
    public static final int TYPE_NO_STRAIGHT_ON = 3;
    public static final int TYPE_ONLY_RIGHT_TURN = 4;
    public static final int TYPE_ONLY_LEFT_TURN = 5;
    public static final int TYPE_ONLY_STRAIGHT_ON = 6;
    public static final int TYPE_NO_U_TURN = 7;

    private long fromOsm;
    private long viaOsm;
    private long toOsm;
    private int restriction;

    private OSMTurnRelation( OSMRelation parent )
    {
        super(parent.id, parent.tags);
        this.members = parent.members;
    }

    private boolean isValid()
    {
        return restriction != TYPE_UNSUPPORTED && viaOsm >= 0 && fromOsm >= 0 && toOsm >= 0;
    }

    /**
     * transforms this relation into a collection of node cost entries
     * <p>
     * @param edgeOutExplorer an edge filter which only allows outgoing edges
     * @param edgeInExplorer an edge filter which only allows incoming edges
     * @return a collection of node cost entries which can be added to the graph later
     */
    public Collection<TurnCostTableEntry> getRestrictionAsEntries( TurnCostEncoder encoder,
            EdgeExplorer edgeOutExplorer, EdgeExplorer edgeInExplorer, OSMReader osmReader )
    {
        final Set<TurnCostTableEntry> entries = new HashSet<TurnCostTableEntry>();

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
                if (this.restriction == TYPE_NO_U_TURN || this.restriction == TYPE_NO_LEFT_TURN
                        || this.restriction == TYPE_NO_RIGHT_TURN || this.restriction == TYPE_NO_STRAIGHT_ON)
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

                } else if (this.restriction == TYPE_ONLY_RIGHT_TURN || this.restriction == TYPE_ONLY_LEFT_TURN
                        || this.restriction == TYPE_ONLY_STRAIGHT_ON)
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
    public static class TurnCostTableEntry
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
    }

    /**
     * Creates an OSM turn relation out of an unspecified OSM relation
     * <p>
     * @author karl.huebner
     */
    static class FactoryEngine implements OSMRelationFactoryEngine<OSMTurnRelation>
    {

        @Override
        public OSMTurnRelation create( OSMRelation relation )
        {
            if ("restriction".equals(relation.getTag("type")))
            {
                OSMTurnRelation turnRelation = new OSMTurnRelation(relation);
                turnRelation.restriction = getRestrictionType(relation.getTag("restriction"));
                for (OSMRelation.Member member : relation.getMembers())
                {
                    if (OSMElement.WAY == member.type())
                    {
                        if ("from".equals(member.role()))
                        {
                            turnRelation.fromOsm = member.ref();
                        } else if ("to".equals(member.role()))
                        {
                            turnRelation.toOsm = member.ref();
                        }
                    } else if (OSMElement.NODE == member.type() && "via".equals(member.role()))
                    {
                        turnRelation.viaOsm = member.ref();
                    }
                }
                if (turnRelation.isValid())
                {
                    return turnRelation;
                }

            }
            return null;
        }

        private int getRestrictionType( String restrictionType )
        {
            if ("no_left_turn".equals(restrictionType))
            {
                return OSMTurnRelation.TYPE_NO_LEFT_TURN;
            } else if ("no_right_turn".equals(restrictionType))
            {
                return OSMTurnRelation.TYPE_NO_RIGHT_TURN;
            } else if ("no_straight_on".equals(restrictionType))
            {
                return OSMTurnRelation.TYPE_NO_STRAIGHT_ON;
            } else if ("no_u_turn".equals(restrictionType))
            {
                return OSMTurnRelation.TYPE_NO_U_TURN;
            } else if ("only_right_turn".equals(restrictionType))
            {
                return OSMTurnRelation.TYPE_ONLY_RIGHT_TURN;
            } else if ("only_left_turn".equals(restrictionType))
            {
                return OSMTurnRelation.TYPE_ONLY_LEFT_TURN;
            } else if ("only_straight_on".equals(restrictionType))
            {
                return OSMTurnRelation.TYPE_ONLY_STRAIGHT_ON;
            }
            return OSMTurnRelation.TYPE_UNSUPPORTED;
        }

    }

}
