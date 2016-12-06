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
package com.graphhopper.reader.gtfs;

import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Iterator;

class Label {

    final long currentTime;

    final int edge;
    final int adjNode;

    final int nTransfers;
    final long firstPtDepartureTime;
    final Label parent;

    Label(long currentTime, int edgeId, int adjNode, int nTransfers, long firstPtDepartureTime, Label parent) {
        this.currentTime = currentTime;
        this.edge = edgeId;
        this.adjNode = adjNode;
        this.nTransfers = nTransfers;
        this.firstPtDepartureTime = firstPtDepartureTime;
        this.parent = parent;
    }

    @Override
    public String toString() {
        return adjNode + " (" + edge + ") time: " + currentTime;
    }

    static Iterable<EdgeIteratorState> reverseEdges(Label leaf, Graph graph) {
        return new Iterable<EdgeIteratorState>() {
            @Override
            public Iterator<EdgeIteratorState> iterator() {
                return new Iterator<EdgeIteratorState>() {
                    Label label = leaf;
                    @Override
                    public boolean hasNext() {
                        return label.parent != null;
                    }

                    @Override
                    public EdgeIteratorState next() {
                        EdgeIteratorState edgeIteratorState = graph.getEdgeIteratorState(label.edge, label.parent.adjNode);
                        label = label.parent;
                        return edgeIteratorState;
                    }
                };
            }
        };
    }

}
