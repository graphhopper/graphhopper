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
import com.graphhopper.storage.RoutingCHEdgeIteratorState;
import com.graphhopper.core.util.EdgeIterator;

public class CHEntry extends SPTEntry {
    /**
     * The id of the incoming original edge at this shortest path tree entry. For original edges this is the same
     * as the edge id, but for shortcuts this is the id of the last original edge of the shortcut.
     *
     * @see RoutingCHEdgeIteratorState#getOrigEdgeKeyLast()
     */
    public int incEdge;

    public CHEntry(int node, double weight) {
        this(EdgeIterator.NO_EDGE, EdgeIterator.NO_EDGE, node, weight, null);
    }

    public CHEntry(int edge, int incEdge, int adjNode, double weight, SPTEntry parent) {
        super(edge, adjNode, weight, parent);
        this.incEdge = incEdge;
    }

    public CHEntry getParent() {
        return (CHEntry) super.parent;
    }

    @Override
    public String toString() {
        return super.toString() + ", incEdge: " + incEdge;
    }
}
