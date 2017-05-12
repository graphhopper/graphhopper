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

    static class Transition {
        final Label label;
        final EdgeLabel edge;

        Transition(Label label, EdgeLabel edge) {
            this.label = label;
            this.edge = edge;
        }

        @Override
        public String toString() {
            return (edge != null ? edge.toString() + " -> " : "") + label.adjNode;
        }

    }

    static class EdgeLabel {
        final EdgeIteratorState edgeIteratorState;
        final GtfsStorage.EdgeType edgeType;
        final int timeZoneId;
        final int nTransfers;
        final double distance;

        public EdgeLabel(EdgeIteratorState edgeIteratorState, GtfsStorage.EdgeType edgeType, int timeZoneId, int nTransfers, double distance) {
            this.edgeIteratorState = edgeIteratorState;
            this.edgeType = edgeType;
            this.timeZoneId = timeZoneId;
            this.nTransfers = nTransfers;
            this.distance = distance;
        }

        @Override
        public String toString() {
            return edgeType.toString();
        }
    }

    final long currentTime;

    final int edge;
    final int adjNode;

    final int nTransfers;
    final int nWalkDistanceConstraintViolations;

    final double walkDistanceOnCurrentLeg;
    final long firstPtDepartureTime;

    final Label parent;

    Label(long currentTime, int edgeId, int adjNode, int nTransfers, int nWalkDistanceConstraintViolations, double walkDistance, long firstPtDepartureTime, Label parent) {
        this.currentTime = currentTime;
        this.edge = edgeId;
        this.adjNode = adjNode;
        this.nTransfers = nTransfers;
        this.nWalkDistanceConstraintViolations = nWalkDistanceConstraintViolations;
        this.walkDistanceOnCurrentLeg = walkDistance;
        this.firstPtDepartureTime = firstPtDepartureTime;
        this.parent = parent;
    }

    @Override
    public String toString() {
        return adjNode + " (" + edge + ") time: " + currentTime;
    }

    static Iterable<Transition> reverseEdges(Label leaf, Graph graph, PtFlagEncoder flagEncoder, boolean reverseEdgeFlags) {
        return new Iterable<Transition>() {
            @Override
            public Iterator<Transition> iterator() {
                return new Iterator<Transition>() {
                    int i = 0;
                    Label label = leaf;
                    @Override
                    public boolean hasNext() {
                        return reverseEdgeFlags ? label != null : label.parent != null;
                    }

                    @Override
                    public Transition next() {
                        if (i==0 && !reverseEdgeFlags) {
                            ++i;
                            return new Transition(label, null);
                        } else {
                            EdgeIteratorState edgeIteratorState = label.parent == null ? null : graph.getEdgeIteratorState(label.edge, label.parent.adjNode).detach(reverseEdgeFlags);
                            Transition transition;
                            if (reverseEdgeFlags) {
                                transition = new Transition(label, edgeIteratorState != null ? getEdgeLabel(edgeIteratorState, flagEncoder) : null);
                            } else {
                                transition = new Transition(label.parent, edgeIteratorState != null ? getEdgeLabel(edgeIteratorState, flagEncoder) : null);
                            }
                            label = label.parent;
                            return transition;
                        }
                    }
                };
            }
        };
    }

    private static EdgeLabel getEdgeLabel(EdgeIteratorState edgeIteratorState, PtFlagEncoder flagEncoder) {
        return new EdgeLabel(edgeIteratorState, flagEncoder.getEdgeType(edgeIteratorState.getFlags()), flagEncoder.getValidityId(edgeIteratorState.getFlags()), flagEncoder.getTransfers(edgeIteratorState.getFlags()), edgeIteratorState.getDistance());
    }

}
