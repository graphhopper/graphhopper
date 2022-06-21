package com.graphhopper.routing.matrix.algorithm;

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.EdgeExplorer;

/**
 * Base class custom MatrixAlgorithm implementations
 *
 * @author Pascal Büttiker
 */
public abstract class AbstractMatrixAlgorithm implements MatrixAlgorithm {


    protected final RoutingCHGraph chGraph;
    protected NodeAccess nodeAccess;
    protected final EdgeExplorer edgeExplorer;
    protected final Weighting weighting;
    protected final TraversalMode traversalMode;

    /**
     * @param graph         specifies the graph where this algorithm will run on
     * @param weighting     set the used weight calculation (e.g. fastest, shortest).
     * @param traversalMode how the graph is traversed e.g. if via nodes or edges.
     */
    public AbstractMatrixAlgorithm(RoutingCHGraph graph, Weighting weighting, TraversalMode traversalMode, NodeAccess nodeAccess) {
        this.weighting = weighting;
        this.traversalMode = traversalMode;
        this.chGraph = graph;
        this.nodeAccess = chGraph.getBaseGraph().getNodeAccess();
        this.edgeExplorer = chGraph.getBaseGraph().createEdgeExplorer();
        this.nodeAccess = nodeAccess;
    }
}
