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
import com.graphhopper.util.EdgeIteratorState;

import java.util.List;

/**
 * This class calculates the {@link PathDetails} in a similar fashion to the instruction calculation,
 * also see {@link com.graphhopper.routing.InstructionsFromEdges}.
 * <p>
 * This class uses the {@link PathDetailsCalculator}. We server every edge into the calculator to see
 * if the calculator value changes, if yes, we create a new interval of {@link PathDetails}.
 *
 * @author Robin Boldt
 */
public class PathDetailsFromEdges implements Path.EdgeVisitor {

    private final List<PathDetailsCalculator> calculators;
    private int numberOfPoints = 0;

    public PathDetailsFromEdges(List<PathDetailsCalculator> calculators) {
        this.calculators = calculators;
    }

    @Override
    public void next(EdgeIteratorState edge, int index, int prevEdgeId) {
        for (PathDetailsCalculator calc : calculators) {
            if (calc.isEdgeDifferentToLastEdge(edge)) {
                calc.getPathDetailsBuilder().endInterval(numberOfPoints);
                calc.getPathDetailsBuilder().startInterval(calc.getCurrentValue());
                numberOfPoints = 0;
            }
        }
        numberOfPoints += edge.fetchWayGeometry(2).size();
    }

    @Override
    public void finish() {
        for (PathDetailsCalculator calc : calculators) {
            calc.getPathDetailsBuilder().endInterval(numberOfPoints);
        }
    }
}