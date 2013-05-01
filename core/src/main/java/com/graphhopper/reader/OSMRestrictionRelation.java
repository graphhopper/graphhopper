package com.graphhopper.reader;

import java.util.ArrayList;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.GraphNodeCosts;
import com.graphhopper.storage.NodeCostsEntry;
import com.graphhopper.util.EdgeIterator;

/**
 * Helper object which gives node cost entries
 * for a given OSM-relation of type "restriction"
 */
public class OSMRestrictionRelation {
    
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final int TYPE_UNSUPPORTED = 0;
    public static final int TYPE_NO_LEFT_TURN = 1;
    public static final int TYPE_NO_RIGHT_TURN = 2;
    public static final int TYPE_NO_STRAIGHT_ON = 3;
    public static final int TYPE_ONLY_RIGHT_TURN = 4;
    public static final int TYPE_ONLY_LEFT_TURN = 5;
    public static final int TYPE_ONLY_STRAIGHT_ON = 6;
    public static final int TYPE_NO_U_TURN = 7;

    protected long osmFrom;
    protected int via;
    protected long osmTo;
    protected int restriction;
    protected boolean restrictionTypeFound;

    /**
     * @return <code>true</code>, if restriction type is supported and a via node has been found
     */
    public boolean isValid() {
        return restrictionTypeFound && restriction != TYPE_UNSUPPORTED && via >= 0;
    }

    /**
     * transforms this relation into a collection of node cost entries
     * 
     * @param g the graph which provides node cost tables
     * @param edgeOutFilter an edge filter which only allows outgoing edges
     * @param edgeInFilter an edge filter which only allows incoming edges
     * @return a collection of node cost entries which can be added to the graph later
     */
    public Collection<NodeCostsEntry> getAsEntries(GraphNodeCosts g,
            EdgeFilter edgeOutFilter, EdgeFilter edgeInFilter) {
        Collection<NodeCostsEntry> entries = new ArrayList<NodeCostsEntry>(3);
        if (via == EdgeIterator.NO_EDGE) {
            return entries;
        }
        try {
            
            // get all incoming edges and receive the edge which is defined by osmFrom 
            final EdgeIterator edgesIn = g.getEdges(via, edgeInFilter);
            EdgeIterator edgeFrom = null;
            do {
                if (g.getEdgeOsmId(edgesIn.edge()) == osmFrom) {
                    edgeFrom = edgesIn;
                    break;
                }
            } while (edgesIn.next());
            
            //get all outgoing edges of the via node 
            final EdgeIterator edgesOut = g.getEdges(via, edgeOutFilter);

            if (edgeFrom != null) {
                if (restriction == TYPE_NO_U_TURN
                        || restriction == TYPE_NO_LEFT_TURN
                        || restriction == TYPE_NO_RIGHT_TURN
                        || restriction == TYPE_NO_STRAIGHT_ON) {
                    // if we have a restriction of TYPE_NO_* we add infinite costs only to
                    // the given turn (from, via, to)  
                    do {
                        if (edgesOut.edge() != edgeFrom.edge()
                                && g.getEdgeOsmId(edgesOut.edge()) == osmTo) {
                            entries.add(new NodeCostsEntry()
                                    .costs(Double.MAX_VALUE).node(via)
                                    .edgeFrom(edgeFrom.edge())
                                    .edgeTo(edgesOut.edge()));
                        }
                    } while (edgesOut.next());

                } else if (restriction == TYPE_ONLY_RIGHT_TURN
                        || restriction == TYPE_ONLY_LEFT_TURN
                        || restriction == TYPE_ONLY_STRAIGHT_ON) {
                    // if we have a restriction of TYPE_ONLY_* we add infinite costs to
                    // any other turn possibility (from, via, * )
                    do {
                        if (edgesOut.edge() != edgeFrom.edge()
                                && g.getEdgeOsmId(edgesOut.edge()) != osmTo) {
                            entries.add(new NodeCostsEntry()
                                    .costs(Double.MAX_VALUE).node(via)
                                    .edgeFrom(edgeFrom.edge())
                                    .edgeTo(edgesOut.edge()));
                        }
                    } while (edgesOut.next());
                }
            }
        } catch (Exception e) {
            logger.warn("Could not built node costs table for relation of node "+via+".", e);
        }
        //TODO remove duplicate entries
        return entries;

    }

}
