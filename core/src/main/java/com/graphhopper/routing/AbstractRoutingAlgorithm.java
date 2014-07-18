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
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Karich
 */
public abstract class AbstractRoutingAlgorithm implements RoutingAlgorithm
{
    private EdgeFilter additionalEdgeFilter;
    protected Graph graph;
    protected NodeAccess nodeAccess;
    protected EdgeExplorer inEdgeExplorer;
    protected EdgeExplorer outEdgeExplorer;
    protected final Weighting weighting;
    protected final FlagEncoder flagEncoder;
    private final boolean edgeBased;
    private boolean alreadyRun;

    /**
     * @param graph specifies the graph where this algorithm will run on
     * @param encoder sets the used vehicle (bike, car, foot)
     * @param weighting set the used weight calculation (e.g. fastest, shortest).
     * @param edgeBased if true edges are traversed whilst considering its direction which is
     * required to support turn costs and restrictions
     */
    public AbstractRoutingAlgorithm( Graph graph, FlagEncoder encoder, Weighting weighting, boolean edgeBased )
    {
        this.weighting = weighting;
        this.flagEncoder = encoder;
        this.edgeBased = edgeBased;
        setGraph(graph);
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
        if (edgeBased)
            return GHUtility.createEdgeKey(iter.getAdjNode(), iter.getBaseNode(), iter.getEdge(), reverse);

        return iter.getAdjNode();
    }

    protected boolean isEdgeBased()
    {
        return edgeBased;
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
        if (!edgeBased && iter.getEdge() == prevOrNextEdgeId)
            return false;

        return additionalEdgeFilter == null || additionalEdgeFilter.accept(iter);
    }

    protected void updateBestPath( EdgeIteratorState edgeState, EdgeEntry bestEdgeEntry, int key )
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
