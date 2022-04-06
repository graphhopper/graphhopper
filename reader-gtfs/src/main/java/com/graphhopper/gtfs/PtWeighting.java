package com.graphhopper.gtfs;

import com.graphhopper.gtfs.PtGraph.PtEdge;

public class PtWeighting {

    public static final double DEFAULT_BETA_PT_EDGE_TIME = 1.0;
    private double betaPtEdgeTime;

    public PtWeighting(double betaPtEdgeTime) {
        this.betaPtEdgeTime = betaPtEdgeTime;
    }
    
    public double calcEdgeWeight(PtEdge edge) {
        return edge.getTime() * betaPtEdgeTime;
    }

}
