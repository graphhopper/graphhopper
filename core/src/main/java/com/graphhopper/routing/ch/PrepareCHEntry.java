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

public class PrepareCHEntry {
    /**
     * The edge key of the incoming original edge at this shortest path tree entry. For original edges this is the same
     * as the edge key, but for shortcuts this is the edge key of the last original edge of the shortcut.
     */
    public int incEdgeKey;
    /**
     * The ID of the edge associated with this entry in the prepare graph (this is not the same number that will later
     * be the ID of the edge/shortcut in the CHGraph.
     */
    public int prepareEdge;
    public int adjNode;
    public double weight;
    public PrepareCHEntry parent;

    public PrepareCHEntry(int prepareEdge, int incEdgeKey, int adjNode, double weight) {
        this.prepareEdge = prepareEdge;
        this.adjNode = adjNode;
        this.weight = weight;
        this.incEdgeKey = incEdgeKey;
    }

    public PrepareCHEntry getParent() {
        return parent;
    }

    @Override
    public String toString() {
        return (adjNode + " (" + prepareEdge + ") weight: " + weight) + ", incEdgeKey: " + incEdgeKey;
    }

}
