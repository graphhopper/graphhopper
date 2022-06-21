package com.graphhopper.routing.matrix.algorithm;

import com.graphhopper.routing.matrix.MatrixEntry;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.storage.*;

public class ManyToManyNode extends AbstractManyToMany {

    private TraversalMode traversalMode = TraversalMode.NODE_BASED;


    public ManyToManyNode(QueryRoutingCHGraph graph){

        super(graph);

        if (graph.hasTurnCosts())
            throw new IllegalStateException("Weightings supporting turn costs cannot be used with node-based traversal mode");
    }

    @Override
    protected int getTraversalId(RoutingCHEdgeIteratorState state, int origEdgeId,Boolean reverse){
        return traversalMode.createTraversalId(state.getBaseNode(),state.getAdjNode(),state.getEdge(),reverse);
    }

    @Override
    protected boolean accept(RoutingCHEdgeIteratorState edge, MatrixEntry currEdge) {
        if(edge.getEdge() == getIncomingEdge(currEdge))
            return false;
        else
            return levelEdgeFilter == null || levelEdgeFilter.accept(edge);
    }

   @Override
    protected double calcWeight(RoutingCHEdgeIteratorState iter, MatrixEntry currEdge, boolean reverse){
        return iter.getWeight(reverse) + currEdge.getWeightOfVisitedPath();
    }

    @Override
    protected long calcTime(RoutingCHEdgeIteratorState iter, MatrixEntry currEdge, boolean reverse){
        return iter.getTime(reverse) + currEdge.time;
    }

    @Override
    protected double calcDistance(RoutingCHEdgeIteratorState iter, MatrixEntry currEdge){
        return iter.getDistance() + currEdge.distance;
    }

    @Override
    public String getName(){
        return getClass().getSimpleName();
    }
}