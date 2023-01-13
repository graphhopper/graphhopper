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

package com.graphhopper.routing.ch;

import com.graphhopper.routing.SPTEntry;
import com.graphhopper.core.util.EdgeIterator;

public class AStarCHEntry extends CHEntry {
    public double weightOfVisitedPath;

    public AStarCHEntry(int node, double heapWeight, double weightOfVisitedPath) {
        this(EdgeIterator.NO_EDGE, EdgeIterator.NO_EDGE, node, heapWeight, weightOfVisitedPath, null);
    }

    public AStarCHEntry(int edge, int incEdge, int adjNode, double heapWeight, double weightOfVisitedPath, SPTEntry parent) {
        super(edge, incEdge, adjNode, heapWeight, parent);
        this.weightOfVisitedPath = weightOfVisitedPath;
    }

    @Override
    public AStarCHEntry getParent() {
        return (AStarCHEntry) super.getParent();
    }

    @Override
    public double getWeightOfVisitedPath() {
        return weightOfVisitedPath;
    }
}
