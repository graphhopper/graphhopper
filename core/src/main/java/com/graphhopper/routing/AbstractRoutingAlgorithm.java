/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Karich
 */
public abstract class AbstractRoutingAlgorithm implements RoutingAlgorithm
{
    /**
     * For turn costs we need two directions per edge
     */
    public static int HIGHEST_BIT_ONE = 0x80000000;

    private EdgeFilter additionalEdgeFilter;
    protected Graph graph;
    protected NodeAccess nodeAccess;
    protected EdgeExplorer inEdgeExplorer;
    protected EdgeExplorer outEdgeExplorer;
    protected final Weighting weighting;
    protected final FlagEncoder flagEncoder;
    private TRAVERSAL_MODE traversalMode = TRAVERSAL_MODE.NODE_BASED;
    private boolean alreadyRun;

    /**
     * @param graph specifies the graph where this algorithm will run on
     * @param encoder sets the used vehicle (bike, car, foot)
     * @param weighting set the used weight calculation (e.g. fastest, shortest).
     */
    public AbstractRoutingAlgorithm( Graph graph, FlagEncoder encoder, Weighting weighting )
    {
        this.weighting = weighting;
        this.flagEncoder = encoder;
        setGraph(graph);
    }

    /**
     * Sets the mode of traversal.<br>
     * use {@link TRAVERSAL_MODE#NODE_BASED} for node-based behavior (default), consideration of
     * turn restrictions might lead to wrong paths<br>
     * use {@link TRAVERSAL_MODE#EDGE_BASED_DIRECTION_SENSITIVE} for edge-based behavior considering
     * the directions of edges in order to complete of support turn restrictions and complex P-turns
     * in the resulting path<br><br>
     * Be careful: the implementing routing algorithm might not be able to support one of those
     * traversal modes
     * <p>
     * @param traversalMode
     */
    public void setTraversalMode( TRAVERSAL_MODE traversalMode )
    {
        if (!isTraversalModeSupported(traversalMode))
            throw new IllegalArgumentException("Traversal mode " + traversalMode + " is not supported by " + getName());

        this.traversalMode = traversalMode;
    }

    /**
     * Determines which traversal modes are supported by the routing algorithm. By default, only
     * node based behavior is supported. The routing algorithm needs to override this method in
     * order to define its supported traversal behavior.
     * <p>
     * @return if the specified traversal mode is supported
     */
    boolean isTraversalModeSupported( TRAVERSAL_MODE aTraversalMode )
    {
        if (aTraversalMode == TRAVERSAL_MODE.NODE_BASED)
        {
            return true;
        }
        return false;
    }

    /**
     * Returns the identifier to access the map of the shortest weight tree according to the
     * traversal mode. E.g. returning the adjacent node id in node-based behavior whilst returning
     * the edge id in edge-based behavior
     * <p>
     * @param iter the current {@link EdgeIterator}
     * @param reverse <code>true</code>, if traversal in backward direction (bidirectional path
     * searches)
     * @return the identifier to access the shortest weight tree
     */
    protected int createIdentifier( EdgeIterator iter, boolean reverse )
    {
        if (traversalMode == TRAVERSAL_MODE.NODE_BASED)
            return iter.getAdjNode();

        else if (traversalMode == TRAVERSAL_MODE.EDGE_BASED_DIRECTION_SENSITIVE)
        {
            int baseNode = iter.getBaseNode(), adjNode = iter.getAdjNode();
            if (!reverse && baseNode > adjNode || reverse && baseNode < adjNode)
                return iter.getEdge() | HIGHEST_BIT_ONE;

            return iter.getEdge();
        }

        throw new IllegalStateException("Traversal mode " + traversalMode + " is not valid");
    }

    protected boolean isTraversalNodeBased()
    {
        return traversalMode == TRAVERSAL_MODE.NODE_BASED;
    }

    protected boolean isTraversalEdgeBased()
    {
        return traversalMode == TRAVERSAL_MODE.EDGE_BASED_DIRECTION_SENSITIVE;
    }

    /**
     * Specify the graph on which this algorithm should operate. API glitch: this method overwrites
     * graph specified while constructing the algorithm. Only necessary if graph is a QueryGraph.
     */
    protected RoutingAlgorithm setGraph( Graph graph )
    {
        this.graph = graph;
        this.nodeAccess = graph.getNodeAccess();
        outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(flagEncoder, false, true));
        inEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(flagEncoder, true, false));
        return this;
    }

    protected QueryGraph createQueryGraph()
    {
        return new QueryGraph(graph);
    }

    @Override
    public Path calcPath( QueryResult fromRes, QueryResult toRes )
    {
        QueryGraph queryGraph = createQueryGraph();
        List<QueryResult> results = new ArrayList<QueryResult>(2);
        results.add(fromRes);
        results.add(toRes);
        queryGraph.lookup(results);
        setGraph(queryGraph);
        return calcPath(fromRes.getClosestNode(), toRes.getClosestNode());
    }

    public RoutingAlgorithm setEdgeFilter( EdgeFilter additionalEdgeFilter )
    {
        this.additionalEdgeFilter = additionalEdgeFilter;
        return this;
    }

    protected boolean accept( EdgeIterator iter, int prevOrNextEdgeId )
    {
        return iter.getEdge() != prevOrNextEdgeId && (additionalEdgeFilter == null || additionalEdgeFilter.accept(iter));
    }

    protected void updateBestPath( EdgeEntry shortestDE, int currLoc )
    {
    }

    protected void checkAlreadyRun()
    {
        if (alreadyRun)
            throw new IllegalStateException("Create a new instance per call");

        alreadyRun = true;
    }

    protected EdgeEntry createEdgeEntry( int node, double dist )
    {
        return new EdgeEntry(EdgeIterator.NO_EDGE, node, dist);
    }

    /**
     * To be overwritten from extending class. Should we make this available in RoutingAlgorithm
     * interface?
     * <p/>
     * @return true if finished.
     */
    protected abstract boolean finished();

    /**
     * To be overwritten from extending class. Should we make this available in RoutingAlgorithm
     * interface?
     * <p/>
     * @return true if finished.
     */
    protected abstract Path extractPath();

    protected Path createEmptyPath()
    {
        return new Path(graph, flagEncoder);
    }

    @Override
    public String getName()
    {
        return getClass().getSimpleName();
    }

    @Override
    public String toString()
    {
        return getName() + "|" + weighting;
    }
}
