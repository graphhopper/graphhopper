package com.graphhopper.routing.util;

import com.graphhopper.storage.TurnCostStorage;

/**
 * Provides the storage required by turn cost calculation
 * 
 * @author Karl Hübner
 */
public abstract class AbstractTurnWeighting implements TurnWeighting
{

    private boolean enabledTurnRestrictions = false;
    private boolean enabledTurnCosts = false;

    /**
     * Storage, which contains the turn flags
     */
    protected TurnCostStorage turnCostStorage;

    /**
     * Encoder, which decodes the turn flags
     */
    protected TurnCostEncoder turnCostEncoder;

    public AbstractTurnWeighting( TurnCostEncoder encoder )
    {
        this.turnCostEncoder = encoder;
    }

    /**
     * Is required to inject the storage containing the turn flags
     */
    @Override
    public void initTurnWeighting( TurnCostStorage turnCostStorage )
    {
        this.turnCostStorage = turnCostStorage;
    }

    /**
     * enables/disables the turn weight / restrictions
     */
    @Override
    public void setEnableTurnWeighting( boolean turnRestrictions, boolean turnCosts )
    {
        this.enabledTurnRestrictions = turnRestrictions;
        this.enabledTurnCosts = turnCosts;
    }

    @Override
    public boolean isEnabledTurnCosts()
    {
        return enabledTurnCosts;
    }

    @Override
    public boolean isEnabledTurnRestrictions()
    {
        return enabledTurnRestrictions;
    }

    @Override
    public double calcTurnWeight( int edgeFrom, int nodeVia, int edgeTo, boolean reverse )
    {
        if (!isEnabledTurnCosts() && !isEnabledTurnRestrictions())
        {
            return 0;
        }

        if (turnCostStorage == null)
        {
            throw new AssertionError("No storage set to calculate turn weight");
        }
        if (turnCostEncoder == null)
        {
            throw new AssertionError("No encoder set to calculate turn weight");
        }

        if (reverse)
        {
            return calcTurnWeight(edgeTo, nodeVia, edgeFrom);
        } else
        {
            return calcTurnWeight(edgeFrom, nodeVia, edgeTo);
        }
    }

    protected abstract double calcTurnWeight( int edgeTo, int nodeVia, int edgeFrom );

}
