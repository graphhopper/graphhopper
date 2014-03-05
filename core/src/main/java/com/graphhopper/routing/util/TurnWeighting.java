package com.graphhopper.routing.util;

import com.graphhopper.storage.TurnCostStorage;

/**
 * Provides methods to retrieve turn costs for a specific turn.
 * 
 * @author Karl Hübner
 */
public interface TurnWeighting
{

    /**
     * Initializes the weighting by injecting the turn cost storage
     * 
     * @param turnCostStorage the turn cost storage to be used
     */
    void initTurnWeighting(TurnCostStorage turnCostStorage);
    
    /**
     * @return the calculated weight of the ongoing turn
     */
    double calcTurnWeight( int edgeFrom, int nodeVia, int edgeTo, boolean reverse );
    
    /**
     * @param turnRestrictions <code>true</code> if turn restrictions should be considered
     * @param turnCosts <code>true</code> if turn costs should be considered
     */
    void setEnableTurnWeighting(boolean turnRestrictions, boolean turnCosts);
    
    /**
     * @return <code>true</code> if turn restrictions are considered by this weighting
     */
    boolean isEnabledTurnRestrictions();
    
    /**
     * @return <code>true</code> if turn costs are considered by this weighting
     */
    boolean isEnabledTurnCosts();
    
    
}
