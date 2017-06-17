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
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;

import java.util.List;

/**
 * This class calculates the PathDetails, similar to the instruction calculation
 *
 * @author Robin Boldt
 */
public class PathDetailsFromEdges implements Path.EdgeVisitor{

    int i;
    PathDetailsCalculator calc;
    int pointIndex = 0;

    double lat, lng;
    int adjNode;

    private final List<PathDetails> details;
    private final List<PathDetailsCalculator> calculators;
    private final PointList points;
    private final NodeAccess nodeAccess;

    public PathDetailsFromEdges(List<PathDetails> details, List<PathDetailsCalculator> calculators, PointList points, NodeAccess nodeAccess){
        this.details = details;
        this.calculators = calculators;
        this.points = points;
        this.nodeAccess = nodeAccess;
    }

    @Override
    public void next(EdgeIteratorState edge, int index, int prevEdgeId) {
        for (i = 0; i < calculators.size(); i++) {
            calc = calculators.get(i);
            if (calc.edgeIsDifferentToLastEdge(edge)) {
                // Is currently called for every calc edgeIsDifferentToLastEdge=true, but should be fast
                pointIndex = findMatchingPointIndex(edge.getBaseNode(), pointIndex);

                details.get(i).endInterval(pointIndex);
                details.get(i).startInterval(calc.getCurrentValue(), pointIndex);
            }
        }
        // We need this in the finish() step
        adjNode = edge.getAdjNode();
    }

    private int findMatchingPointIndex(int baseNode, int pointIndex) {
        lat = nodeAccess.getLat(baseNode);
        lng = nodeAccess.getLon(baseNode);
        for (; pointIndex < points.size(); pointIndex++) {
            if (lat == points.getLat(pointIndex) && lng == points.getLon(pointIndex)) {
                return pointIndex;
            }
        }
        throw new IllegalStateException("Did not find a matching point, did you simplify the points?");
    }

    @Override
    public void finish() {
        pointIndex = findMatchingPointIndex(adjNode, pointIndex);
        for (i = 0; i < details.size(); i++) {
            details.get(i).endInterval(pointIndex);
        }
    }

}
