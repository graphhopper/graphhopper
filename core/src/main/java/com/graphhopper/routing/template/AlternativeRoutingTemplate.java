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
import com.graphhopper.PathWrapper;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.Parameters.Routing;
import com.graphhopper.util.PathMerger;
import com.graphhopper.util.PointList;
import com.graphhopper.util.Translation;

import java.util.Collections;
import java.util.List;

import static com.graphhopper.util.Parameters.Routing.PASS_THROUGH;

import com.graphhopper.util.shapes.GHPoint;

/**
 * Implementation of a route with no via points but multiple path lists ('alternatives').
 *
 * @author Peter Karich
 */
final public class AlternativeRoutingTemplate extends ViaRoutingTemplate {
    public AlternativeRoutingTemplate(GHRequest ghRequest, GHResponse ghRsp, LocationIndex locationIndex) {
        super(ghRequest, ghRsp, locationIndex);
    }

    @Override
    public List<QueryResult> lookup(List<GHPoint> points, FlagEncoder encoder) {
        if (points.size() > 2)
            throw new IllegalArgumentException("Currently alternative routes work only with start and end point. You tried to use: " + points.size() + " points");

        return super.lookup(points, encoder);
    }

    @Override
    public List<Path> calcPaths(QueryGraph queryGraph, RoutingAlgorithmFactory algoFactory, AlgorithmOptions algoOpts) {
        boolean withViaTurnPenalty = ghRequest.getHints().getBool(Routing.PASS_THROUGH, false);
        if (withViaTurnPenalty)
            throw new IllegalArgumentException("Alternative paths and " + PASS_THROUGH + " at the same time is currently not supported");

        return super.calcPaths(queryGraph, algoFactory, algoOpts);
    }

    @Override
    public boolean isReady(PathMerger pathMerger, Translation tr) {
        if (pathList.isEmpty())
            throw new RuntimeException("Empty paths for alternative route calculation not expected");

        // if alternative route calculation was done then create the responses from single paths        
        PointList wpList = getWaypoints();
        altResponse.setWaypoints(wpList);
        ghResponse.add(altResponse);
        pathMerger.doWork(altResponse, Collections.singletonList(pathList.get(0)), tr);
        for (int index = 1; index < pathList.size(); index++) {
            PathWrapper tmpAltRsp = new PathWrapper();
            tmpAltRsp.setWaypoints(wpList);
            ghResponse.add(tmpAltRsp);
            pathMerger.doWork(tmpAltRsp, Collections.singletonList(pathList.get(index)), tr);
        }
        return true;
    }
}
