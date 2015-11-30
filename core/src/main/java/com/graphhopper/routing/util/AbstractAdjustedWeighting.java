package com.graphhopper.routing.util;

/**
 * The AdjustedWeighting wraps another Weighting.
 *
 * @author Robin Boldt
 */
public abstract class AbstractAdjustedWeighting implements Weighting
{

    protected final Weighting superWeighting;

    public AbstractAdjustedWeighting( Weighting superWeighting )
    {
        if (superWeighting == null)
            throw new IllegalArgumentException("No super weighting set");
        this.superWeighting = superWeighting;
    }

    /**
     * Returns the flagEncoder of the superWeighting. Usually we do not have a Flagencoder here.
     */
    @Override
    public FlagEncoder getFlagEncoder()
    {
        return superWeighting.getFlagEncoder();
    }

    @Override
    public boolean matches( String weightingAsStr, FlagEncoder encoder )
    {
        return getName().equals(weightingAsStr) && encoder == superWeighting.getFlagEncoder();
    }

}

