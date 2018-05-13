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

class WitnessSearchEntry extends CHEntry {
    /**
     * Flag used to keep track whether or not a shortest path tree entry represents a path that goes directly (without
     * visiting any other node) from the source node to the center node (the node to be contracted). The path may
     * contain additional loops at the center node.
     */
    boolean isDirectCenterNodePath;

    public WitnessSearchEntry(int edge, int incEdge, int adjNode, double weight, boolean isDirectCenterNodePath) {
        super(edge, incEdge, adjNode, weight);
        this.isDirectCenterNodePath = isDirectCenterNodePath;
    }

    public WitnessSearchEntry getParent() {
        return (WitnessSearchEntry) super.parent;
    }

    @Override
    public String toString() {
        return super.toString() + ", isDirectCenterNodePath: " + isDirectCenterNodePath;
    }
}
