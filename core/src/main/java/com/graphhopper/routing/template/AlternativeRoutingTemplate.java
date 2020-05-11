/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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
package com.graphhopper.routing.template;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.Parameters.Routing;
import com.graphhopper.util.PathMerger;
import com.graphhopper.util.PointList;
import com.graphhopper.util.Translation;
import com.graphhopper.util.shapes.GHPoint;

import java.util.Collections;
import java.util.List;

import static com.graphhopper.util.Parameters.Routing.PASS_THROUGH;

/**
 * Implementation of a route with no via points but multiple path lists ('alternatives').
 *
 * @author Peter Karich
 */
final public class AlternativeRoutingTemplate extends ViaRoutingTemplate {
    public AlternativeRoutingTemplate(GHRequest ghRequest, GHResponse ghRsp, LocationIndex locationIndex,
                                      EncodedValueLookup lookup, Weighting weighting) {
        super(ghRequest, ghRsp, locationIndex, lookup, weighting);
    }

    @Override
    public List<QueryResult> lookup(List<GHPoint> points) {
        if (points.size() > 2)
            throw new IllegalArgumentException("Currently alternative routes work only with start and end point. You tried to use: " + points.size() + " points");

        return super.lookup(points);
    }

    @Override
    public List<Path> calcPaths(QueryGraph queryGraph, RoutingAlgorithmFactory algoFactory, AlgorithmOptions algoOpts) {
        boolean withViaTurnPenalty = ghRequest.getHints().getBool(Routing.PASS_THROUGH, false);
        if (withViaTurnPenalty)
            throw new IllegalArgumentException("Alternative paths and " + PASS_THROUGH + " at the same time is currently not supported");

        return super.calcPaths(queryGraph, algoFactory, algoOpts);
    }

    @Override
    public void finish(PathMerger pathMerger, Translation tr) {
        if (pathList.isEmpty())
            throw new RuntimeException("Empty paths for alternative route calculation not expected");

        // if alternative route calculation was done then create the responses from single paths        
        PointList wpList = getWaypoints();
        responsePath.setWaypoints(wpList);
        ghResponse.add(responsePath);
        pathMerger.doWork(responsePath, Collections.singletonList(pathList.get(0)), lookup, tr);
        for (int index = 1; index < pathList.size(); index++) {
            ResponsePath p = new ResponsePath();
            p.setWaypoints(wpList);
            ghResponse.add(p);
            pathMerger.doWork(p, Collections.singletonList(pathList.get(index)), lookup, tr);
        }
    }
}
