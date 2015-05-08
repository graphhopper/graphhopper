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

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;

/**
 * Common subclass for bidirectional algorithms.
 * <p/>
 * @author Peter Karich
 */
public abstract class AbstractBidirAlgo extends AbstractRoutingAlgorithm
{
    int visitedCountFrom;
    int visitedCountTo;
    protected boolean finishedFrom;
    protected boolean finishedTo;

    abstract void initFrom( int from, double dist );

    abstract void initTo( int to, double dist );

    protected abstract Path createAndInitPath();

    abstract void checkState( int fromBase, int fromAdj, int toBase, int toAdj );

    abstract boolean fillEdgesFrom();

    abstract boolean fillEdgesTo();

    public AbstractBidirAlgo( Graph graph, FlagEncoder encoder, Weighting weighting, TraversalMode tMode )
    {
        super(graph, encoder, weighting, tMode);
    }

    @Override
    public Path calcPath( int from, int to )
    {
        checkAlreadyRun();
        createAndInitPath();
        initFrom(from, 0);
        initTo(to, 0);
        runAlgo();
        return extractPath();
    }

    protected void runAlgo()
    {
        while (!finished() && !isWeightLimitExceeded())
        {
            if (!finishedFrom)
                finishedFrom = !fillEdgesFrom();

            if (!finishedTo)
                finishedTo = !fillEdgesTo();
        }
    }

    @Override
    public int getVisitedNodes()
    {
        return visitedCountFrom + visitedCountTo;
    }
}
