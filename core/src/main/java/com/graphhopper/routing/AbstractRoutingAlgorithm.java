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
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

/**
 * @author Peter Karich
 */
public abstract class AbstractRoutingAlgorithm implements RoutingAlgorithm
{
    private EdgeFilter additionalEdgeFilter;
    protected final Graph graph;
    protected final WeightCalculation weightCalc;
    protected final FlagEncoder flagEncoder;
    protected final EdgeExplorer inEdgeExplorer;
    protected final EdgeExplorer outEdgeExplorer;
    private boolean alreadyRun;

    /**
     * @param graph specifies the graph where this algorithm will run on
     * @param encoder sets the used vehicle (bike, car, foot)
     * @param type set the used weight calculation (e.g. fastest, shortest).
     */
    public AbstractRoutingAlgorithm( Graph graph, FlagEncoder encoder, WeightCalculation type )
    {
        this.graph = graph;
        this.weightCalc = type;
        this.flagEncoder = encoder;
        outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(encoder, false, true));
        inEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(encoder, true, false));
    }

    public RoutingAlgorithm setEdgeFilter( EdgeFilter additionalEdgeFilter )
    {
        this.additionalEdgeFilter = additionalEdgeFilter;
        return this;
    }

    protected boolean accept( EdgeIterator iter )
    {
        return additionalEdgeFilter == null || additionalEdgeFilter.accept(iter);
    }

    protected void updateShortest( EdgeEntry shortestDE, int currLoc )
    {
    }

    protected void checkAlreadyRun()
    {
        if (alreadyRun)
            throw new IllegalStateException("Create a new instance per call");

        alreadyRun = true;
    }

    protected EdgeEntry createEmptyEdgeEntry( int node )
    {
        return new EdgeEntry(EdgeIterator.NO_EDGE, node, 0d);
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
        return getName() + "|" + weightCalc;
    }
}
