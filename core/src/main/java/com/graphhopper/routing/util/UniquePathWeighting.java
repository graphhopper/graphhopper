package com.graphhopper.routing.util;

import com.graphhopper.routing.Path;
import com.graphhopper.util.EdgeIteratorState;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

/**
 * Rates already used Paths worse.
 *
 * @author RobinBoldt
 */
public class UniquePathWeighting extends AbstractAdjustedWeighting
{
    // Contains the EdgeIds of the already visisted Edges
    protected final TIntSet visitedEdges = new TIntHashSet();

    public static int ALREADY_VISISTED_EDGES_PENALTY = 3;

    public UniquePathWeighting( Weighting superWeighting )
    {
        super(superWeighting);
    }

    public void addPath( Path path )
    {
        for (EdgeIteratorState edge : path.calcEdges())
        {
            visitedEdges.add(edge.getEdge());
        }
    }

    @Override
    public double getMinWeight( double distance )
    {
        return superWeighting.getMinWeight(distance);
    }

    @Override
    public double calcWeight( EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId )
    {
        double weight = superWeighting.calcWeight(edgeState, reverse, prevOrNextEdgeId);

        if (visitedEdges.contains(edgeState.getEdge()))
        {
            weight = weight * ALREADY_VISISTED_EDGES_PENALTY;
        }

        return weight;
    }

    @Override
    public String getName()
    {
        return "unique_path";
    }
}