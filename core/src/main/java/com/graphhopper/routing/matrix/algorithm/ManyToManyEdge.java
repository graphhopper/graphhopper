package com.graphhopper.routing.matrix.algorithm;


import com.graphhopper.routing.matrix.MatrixEntry;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.storage.*;
import com.graphhopper.util.GHUtility;


public class ManyToManyEdge extends AbstractManyToMany {

    private TraversalMode traversalMode = TraversalMode.EDGE_BASED;

    public ManyToManyEdge(QueryRoutingCHGraph graph){
        super(graph);

        if (!graph.isEdgeBased()) {
            throw new IllegalArgumentException("Edge-based CH algorithms only work with edge-based CH graphs");
        }
    }

    @Override
    protected int getTraversalId(RoutingCHEdgeIteratorState edge, int origEdgeId, Boolean reverse){

        return traversalMode.createTraversalId(edge.getBaseNode(),edge.getAdjNode(),origEdgeId,reverse);
    }


    private double calcWeight(RoutingCHEdgeIteratorState edgeState, Boolean reverse, int prevOrNextEdgeId){

        double edgeWeight = edgeState.getWeight(reverse);
        final int origEdgeId = reverse ? edgeState.getOrigEdgeLast() : edgeState.getOrigEdgeFirst();
        double turnCosts = reverse
                ? graph.getTurnWeight(origEdgeId, edgeState.getBaseNode(), prevOrNextEdgeId)
                : graph.getTurnWeight(prevOrNextEdgeId, edgeState.getBaseNode(), origEdgeId);
        return edgeWeight + turnCosts;

    }

    @Override
    protected double calcWeight(RoutingCHEdgeIteratorState iter, MatrixEntry currEdge, boolean reverse){
        return calcWeight(iter,reverse,getIncomingEdge(currEdge)) + currEdge.getWeightOfVisitedPath();
    }

    private long calcTime(RoutingCHEdgeIteratorState edgeState, Boolean reverse, int prevOrNextEdgeId){

        long time = edgeState.getTime(reverse);
        int origEdgeId;
        if(reverse){
            origEdgeId = edgeState.getOrigEdgeLast();
        }else{
            origEdgeId = edgeState.getOrigEdgeFirst();
        }
        long turnCost;
        if(reverse){
            turnCost = weighting.calcTurnMillis(origEdgeId,edgeState.getBaseNode(),prevOrNextEdgeId);
        }else{
            turnCost = weighting.calcTurnMillis(prevOrNextEdgeId,edgeState.getBaseNode(),origEdgeId);
        }

        return time + turnCost;
    }

    @Override
    protected long calcTime(RoutingCHEdgeIteratorState iter, MatrixEntry currEdge, boolean reverse){
        return calcTime(iter,reverse,getIncomingEdge(currEdge)) + currEdge.time;
    }

    protected int getIncomingEdge(MatrixEntry entry) {
        return entry.incEdge;
    }

    @Override
    protected int getOrigEdgeId(RoutingCHEdgeIteratorState edge, boolean reverse) {
        return reverse ? edge.getOrigEdgeFirst() : edge.getOrigEdgeLast();
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