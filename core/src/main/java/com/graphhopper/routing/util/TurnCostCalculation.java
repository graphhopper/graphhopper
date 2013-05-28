package com.graphhopper.routing.util;

import com.graphhopper.storage.Graph;

/**
 * Calculates the turn costs for a given turn described
 * by edgeFrom, viaNode and edgeTo.
 * 
 * @author Karl Hübner
 */
public interface TurnCostCalculation {

	/**
	 * Gets the turn costs from <code>fromEdge</code> via <code>viaNode</code> to <code>toEdge</code>
	 *  
	 * @param viaNode the node id for which the turn costs will be calculated
	 * @param fromEdge the edge the turn is coming from
	 * @param toEdge the edge the turn is going to
	 * @return the turn costs for this turn (to be encoded by {@link TurnCostEncoder})
	 */
	double getTurnCosts(int viaNode, int fromEdge, int toEdge);
	
	/**
	 * Sets the graph which can be used for turn costs calculation
	 * 
	 * @param graph
	 */
	TurnCostCalculation graph(Graph graph);
}
