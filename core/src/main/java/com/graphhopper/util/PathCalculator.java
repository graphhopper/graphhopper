/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.util;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.PathWrapper;
import com.graphhopper.routing.*;
import com.graphhopper.storage.index.QueryResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Peter Karich
 */
public class PathCalculator
{
    private final Translation tr;
    private final RoutingAlgorithmFactory tmpAlgoFactory;
    private final PathMerger pathMerger;

    public PathCalculator( Translation tr, PathMerger pathMerger, RoutingAlgorithmFactory tmpAlgoFactory )
    {
        this.tr = tr;
        this.tmpAlgoFactory = tmpAlgoFactory;
        this.pathMerger = pathMerger;
    }

    public List<Path> doWork( GHRequest request, GHResponse ghRsp,
                              List<QueryResult> qResults, QueryGraph queryGraph, AlgorithmOptions algoOpts )
    {
        // Every alternative path makes one AltResponse BUT if via points exists then reuse the altResponse object
        PathWrapper altResponse = new PathWrapper();
        ghRsp.add(altResponse);
        int pointCounts = request.getPoints().size();
        boolean viaTurnPenalty = request.getHints().getBool("pass_through", false);
        long visitedNodesSum = 0;
        QueryResult fromQResult = qResults.get(0);
        List<Path> altPaths = new ArrayList<>(pointCounts - 1);
        boolean isAlternativeRoute = AlgorithmOptions.ALT_ROUTE.equalsIgnoreCase(algoOpts.getAlgorithm());

        if ((isAlternativeRoute || isRoundTrip) && pointCounts > 2)
        {
            ghRsp.addError(new RuntimeException("Via points are not yet supported when alternative paths or round trips are requested. The returned paths would just need an additional identification for the via point index."));
            return Collections.emptyList();
        }

        StopWatch sw;
        for (int placeIndex = 1; placeIndex < pointCounts; placeIndex++)
        {
            if (placeIndex == 1)
            {
                // enforce start direction
                queryGraph.enforceHeading(fromQResult.getClosestNode(), request.getFavoredHeading(0), false);
            } else if (viaTurnPenalty)
            {
                if (isAlternativeRoute)
                    throw new IllegalStateException("Alternative paths and a viaTurnPenalty at the same time is currently not supported");

                // enforce straight start after via stop
                Path prevRoute = altPaths.get(placeIndex - 2);
                EdgeIteratorState incomingVirtualEdge = prevRoute.getFinalEdge();
                queryGraph.enforceHeadingByEdgeId(fromQResult.getClosestNode(), incomingVirtualEdge.getEdge(), false);
            }

            QueryResult toQResult = qResults.get(placeIndex);

            // enforce end direction
            queryGraph.enforceHeading(toQResult.getClosestNode(), request.getFavoredHeading(placeIndex), true);

            sw = new StopWatch().start();
            RoutingAlgorithm algo = tmpAlgoFactory.createAlgo(queryGraph, algoOpts);
            String debug = ", algoInit:" + sw.stop().getSeconds() + "s";

            sw = new StopWatch().start();
            List<Path> pathList = algo.calcPaths(fromQResult.getClosestNode(), toQResult.getClosestNode());
            debug += ", " + algo.getName() + "-routing:" + sw.stop().getSeconds() + "s";
            if (pathList.isEmpty())
                throw new IllegalStateException("At least one path has to be returned for " + fromQResult + " -> " + toQResult);

            for (Path path : pathList)
            {
                if (path.getTime() < 0)
                    throw new RuntimeException("Time was negative. Please report as bug and include:" + request);

                altPaths.add(path);
                debug += ", " + path.getDebugInfo();
            }

            altResponse.addDebugInfo(debug);

            // reset all direction enforcements in queryGraph to avoid influencing next path
            queryGraph.clearUnfavoredStatus();

            visitedNodesSum += algo.getVisitedNodes();
            fromQResult = toQResult;
        }

        if (isAlternativeRoute)
        {
            if (altPaths.isEmpty())
                throw new RuntimeException("Empty paths for alternative route calculation not expected");

            // if alternative route calculation was done then create the responses from single paths
            pathMerger.doWork(altResponse, Collections.singletonList(altPaths.get(0)), tr);
            for (int index = 1; index < altPaths.size(); index++)
            {
                altResponse = new PathWrapper();
                ghRsp.add(altResponse);
                pathMerger.doWork(altResponse, Collections.singletonList(altPaths.get(index)), tr);
            }
        } else if (isRoundTrip)
        {
            pathMerger.doWork(altResponse, altPaths, tr);
        } else
        {
            if (pointCounts - 1 != altPaths.size())
                throw new RuntimeException("There should be exactly one more points than paths. points:" + pointCounts + ", paths:" + altPaths.size());

            pathMerger.doWork(altResponse, altPaths, tr);
        }
        ghRsp.getHints().put("visited_nodes.sum", visitedNodesSum);
        ghRsp.getHints().put("visited_nodes.average", (float) visitedNodesSum / (pointCounts - 1));
        return altPaths;
    }
}
