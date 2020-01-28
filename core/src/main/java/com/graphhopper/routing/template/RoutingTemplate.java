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

import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.PathMerger;
import com.graphhopper.util.Translation;
import com.graphhopper.util.shapes.GHPoint;

import java.util.List;

/**
 * This interface provides steps to create the final GHResponse consisting of multiple Paths (via
 * PathWrappers).
 *
 * @author Peter Karich
 */
public interface RoutingTemplate {
    /**
     * This method takes the query points and returns the looked up QueryResults.
     */
    List<QueryResult> lookup(List<GHPoint> points, FlagEncoder encoder);

    /**
     * This method returns a list of Path objects which then can be merged to serve one route with
     * via points or multiple alternative paths.
     */
    List<Path> calcPaths(QueryGraph queryGraph, RoutingAlgorithmFactory algoFactory, AlgorithmOptions algoOpts, FlagEncoder encoder);

    /**
     * This method merges the returned paths appropriately e.g. all paths from the list into one
     * PathWrapper of GHResponse or multiple (via / round trip).
     */
    void finish(PathMerger pathMerger, Translation tr);

}