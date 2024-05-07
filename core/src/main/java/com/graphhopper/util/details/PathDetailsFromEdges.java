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
package com.graphhopper.util.details;

import com.graphhopper.routing.Path;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class calculates a PathDetail list in a similar fashion to the instruction calculation,
 * also see {@link com.graphhopper.routing.InstructionsFromEdges}.
 * <p>
 * This class uses the {@link PathDetailsBuilder}. We provide every edge to the builder
 * and up to its internals we create a new interval, ie. a new PathDetail in the List.
 *
 * @author Robin Boldt
 * @see PathDetail
 */
public class PathDetailsFromEdges implements Path.EdgeVisitor {

    private final List<PathDetailsBuilder> calculators;
    private int lastIndex = 0;

    public PathDetailsFromEdges(List<PathDetailsBuilder> calculators, int previousIndex) {
        this.calculators = calculators;
        this.lastIndex = previousIndex;
    }

    /**
     * Calculates the PathDetails for a Path. This method will return fast, if there are no calculators.
     *
     * @param pathBuilderFactory Generates the relevant PathBuilders
     * @return List of PathDetails for this Path
     */
    public static Map<String, List<PathDetail>> calcDetails(Path path, EncodedValueLookup evLookup, Weighting weighting,
                                                            List<String> requestedPathDetails, PathDetailsBuilderFactory pathBuilderFactory,
                                                            int previousIndex, Graph graph) {
        if (!path.isFound() || requestedPathDetails.isEmpty())
            return Collections.emptyMap();
        HashSet<String> uniquePD = new HashSet<>(requestedPathDetails.size());
        Collection<String> res = requestedPathDetails.stream().filter(pd -> !uniquePD.add(pd)).collect(Collectors.toList());
        if (!res.isEmpty()) throw new IllegalArgumentException("Do not use duplicate path details: " + res);

        List<PathDetailsBuilder> pathBuilders = pathBuilderFactory.createPathDetailsBuilders(requestedPathDetails, path, evLookup, weighting, graph);
        if (pathBuilders.isEmpty())
            return Collections.emptyMap();

        path.forEveryEdge(new PathDetailsFromEdges(pathBuilders, previousIndex));

        Map<String, List<PathDetail>> pathDetails = new HashMap<>(pathBuilders.size());
        for (PathDetailsBuilder builder : pathBuilders) {
            Map.Entry<String, List<PathDetail>> entry = builder.build();
            List<PathDetail> existing = pathDetails.put(entry.getKey(), entry.getValue());
            if (existing != null)
                throw new IllegalStateException("Some PathDetailsBuilders use duplicate key: " + entry.getKey());
        }

        return pathDetails;
    }

    @Override
    public void next(EdgeIteratorState edge, int index, int prevEdgeId) {
        for (PathDetailsBuilder calc : calculators) {
            if (calc.isEdgeDifferentToLastEdge(edge)) {
                calc.endInterval(lastIndex);
                calc.startInterval(lastIndex);
            }
        }
        lastIndex += edge.fetchWayGeometry(FetchMode.PILLAR_AND_ADJ).size();
    }

    @Override
    public void finish() {
        for (PathDetailsBuilder calc : calculators) {
            calc.endInterval(lastIndex);
        }
    }
}