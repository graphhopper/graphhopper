package com.samsix.graphhopper;

import java.util.HashSet;
import java.util.Set;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.routing.util.WeightingMap;
import com.graphhopper.util.EdgeIteratorState;

public class S6GraphHopper
    extends GraphHopper
{    
    @Override
    public Weighting createWeighting( WeightingMap wMap, FlagEncoder encoder )
    {
        String avoid = wMap.get("avoidEdge", null);
        
        Set<Integer> avoidEdges = null;
        if (avoid != null) {
            String[] edges = avoid.split(",");
            avoidEdges = new HashSet<Integer>(edges.length);
            for (int ii=0; ii < edges.length; ii++) {
                avoidEdges.add(Integer.parseInt(edges[ii].trim()));
            }
        }
        return new NoUTurnWeighting(super.createWeighting(wMap, encoder), avoidEdges);
    }
    
    
    public static class NoUTurnWeighting
        implements Weighting
    {
        public Weighting weighting;
        private Set<Integer> avoidEdges;
       
        public NoUTurnWeighting(final Weighting weighting,
                                final Set<Integer> avoidEdges)
        {
            this.weighting = weighting;
            this.avoidEdges = avoidEdges;
        }
        
        
        @Override
        public double getMinWeight(final double distance)
        {
            return weighting.getMinWeight(distance);
        }

        @Override
        public double calcWeight(final EdgeIteratorState edgeState,
                                 final boolean reverse,
                                 final int prevOrNextEdgeId)
        {
            //
            // Avoid this edge if possible for this routing.
            //
            if (avoidEdges != null && avoidEdges.contains(edgeState.getEdge())) {
                //
                // Just big number. Using Double.INFINITY or even Double.MAX_VALUE will
                // cause it to NEVER pick this edge even if it is the only one. We still need
                // to turn around if it is our only choice!
                //
                return 100000000;
            }
            
            return weighting.calcWeight(edgeState, reverse, prevOrNextEdgeId);
        }
    }
}
