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
        if (encoder instanceof TruckFlagEncoder) {
            return new TruckWeighting();
        }
        
        Weighting defaultWeighting = super.createWeighting(wMap, encoder);
        
        //
        // This was created to avoid doing U-Turns if possible.
        // We specify the edge we just drove on as an edge to avoid if possible.
        // We give it a very large, but not infinite number. If it is the only
        // possibility then it will take it thus indicating a valid u-turn.
        //
        String avoid = wMap.get("avoidEdge", null);
        
        Set<Integer> avoidEdges = null;
        if (avoid != null) {
            String[] edges = avoid.split(",");
            avoidEdges = new HashSet<Integer>(edges.length);
            for (int ii=0; ii < edges.length; ii++) {
                avoidEdges.add(Integer.parseInt(edges[ii].trim()));
            }
            return new AvoidEdgeWeighting(defaultWeighting, avoidEdges);
        }
        
        return defaultWeighting;
    }
    
    
    public static class AvoidEdgeWeighting
        implements Weighting
    {
        public Weighting weighting;
        private Set<Integer> avoidEdges;
       
        public AvoidEdgeWeighting(final Weighting weighting,
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
    
    
    public static class TruckWeighting
        implements
            Weighting
    {
        @Override
        public double getMinWeight(final double distance)
        {
            return 0;
        }

        @Override
        public double calcWeight(final EdgeIteratorState edgeState,
                                 final boolean reverse,
                                 final int prevOrNextEdgeId)
        {
            long flags = edgeState.getFlags();
            //
            // TODO: If flags contain the hgv=designated then make this very favorable (zero);
            // if hgv=destination then make it quite
            // unfavorable. Otherwise, make it a midland value.
            //
            return 0;
        }
    }
}
