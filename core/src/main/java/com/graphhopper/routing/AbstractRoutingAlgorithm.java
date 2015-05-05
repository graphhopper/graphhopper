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

import com.graphhopper.routing.util.*;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

/**
 * @author Peter Karich
 */
public abstract class AbstractRoutingAlgorithm implements RoutingAlgorithm
{
    private EdgeFilter additionalEdgeFilter;
    protected final Graph graph;
    protected NodeAccess nodeAccess;
    protected EdgeExplorer inEdgeExplorer;
    protected EdgeExplorer outEdgeExplorer;
    protected final Weighting weighting;
    protected final FlagEncoder flagEncoder;
    protected final TraversalMode traversalMode;
    protected double weightLimit = Double.MAX_VALUE;
    private boolean alreadyRun;

    /**
     * @param graph specifies the graph where this algorithm will run on
     * @param encoder sets the used vehicle (bike, car, foot)
     * @param weighting set the used weight calculation (e.g. fastest, shortest).
     * @param traversalMode how the graph is traversed e.g. if via nodes or edges.
     */
    public AbstractRoutingAlgorithm( Graph graph, FlagEncoder encoder, Weighting weighting, TraversalMode traversalMode )
    {
        this.weighting = weighting;
        this.flagEncoder = encoder;
        this.traversalMode = traversalMode;
        this.graph = graph;
        this.nodeAccess = graph.getNodeAccess();
        outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(flagEncoder, false, true));
        inEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(flagEncoder, true, false));
    }

    @Override
    public void setWeightLimit( double weight )
    {
        this.weightLimit = weight;
    }

    public RoutingAlgorithm setEdgeFilter( EdgeFilter additionalEdgeFilter )
    {
        this.additionalEdgeFilter = additionalEdgeFilter;
        return this;
    }

    protected boolean accept( EdgeIterator iter, int prevOrNextEdgeId )
    {
        if (!traversalMode.hasUTurnSupport() && iter.getEdge() == prevOrNextEdgeId)
            return false;

        return additionalEdgeFilter == null || additionalEdgeFilter.accept(iter);
    }

    protected void updateBestPath( EdgeIteratorState edgeState, EdgeEntry bestEdgeEntry, int traversalId )
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

    protected abstract boolean isWeightLimitExceeded();

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
