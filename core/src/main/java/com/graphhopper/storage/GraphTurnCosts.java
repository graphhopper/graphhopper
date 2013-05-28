package com.graphhopper.storage;

import com.graphhopper.util.TurnCostIterator;


/**
 * Provides the possibility to store node cost tables
 * for each node, which can be used for more exact routing.
 * 
 * @author Karl Hübner
 */
public interface GraphTurnCosts extends Graph {

    
    /**
     * adds a new cost entry for the node <code>nodeIndex</code>
     */
    void turnCosts(int nodeVia, int edgeFrom, int edgeTo, int flags);
    
    /**
     * @return the turn cost flags for the requested turn
     */
	int turnCosts(int nodeVia, int edgeFrom, int edgeTo);
	
	TurnCostIterator createTurnCostIterable(int nodeVia, int edgeFrom, int edgeTo);

    boolean isTurnCostSupport();
	
}
