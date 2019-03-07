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

import java.time.Instant;
import java.util.Iterator;

public class Label {

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

    public final long currentTime;

    final int edge;
    public final int adjNode;

    final int nTransfers;
    final int nWalkDistanceConstraintViolations;

    final double walkDistanceOnCurrentLeg;
    final Long departureTime;
    final long walkTime;

    final long residualDelay;
    final boolean impossible;

    final Label parent;

    Label(long currentTime, int edgeId, int adjNode, int nTransfers, int nWalkDistanceConstraintViolations, double walkDistance, Long departureTime, long walkTime, long residualDelay, boolean impossible, Label parent) {
        this.currentTime = currentTime;
        this.edge = edgeId;
        this.adjNode = adjNode;
        this.nTransfers = nTransfers;
        this.nWalkDistanceConstraintViolations = nWalkDistanceConstraintViolations;
        this.walkDistanceOnCurrentLeg = walkDistance;
        this.departureTime = departureTime;
        this.walkTime = walkTime;
        this.residualDelay = residualDelay;
        this.impossible = impossible;
        this.parent = parent;
    }

    @Override
    public String toString() {
        return adjNode + " " + Instant.ofEpochMilli(currentTime) + " " + nTransfers + " " + nWalkDistanceConstraintViolations + " " +  (departureTime != null ? Instant.ofEpochMilli(departureTime) : "");
    }

    static Iterable<Transition> reverseEdges(Label leaf, GraphExplorer graph, PtFlagEncoder flagEncoder, boolean reverseEdgeFlags) {
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
                            EdgeIteratorState edgeIteratorState = label.parent == null ? null :
                                    graph.getEdgeIteratorState(label.edge, reverseEdgeFlags ? label.adjNode : label.parent.adjNode).detach(false);
                            if (reverseEdgeFlags && edgeIteratorState != null && (edgeIteratorState.getBaseNode() != label.parent.adjNode || edgeIteratorState.getAdjNode() != label.adjNode)) {
                                throw new IllegalStateException();
                            }
                            if (!reverseEdgeFlags && edgeIteratorState != null && (edgeIteratorState.getAdjNode() != label.parent.adjNode || edgeIteratorState.getBaseNode() != label.adjNode)) {
                                throw new IllegalStateException();
                            }

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
        return new EdgeLabel(edgeIteratorState, flagEncoder.getEdgeType(edgeIteratorState), edgeIteratorState.get(flagEncoder.getValidityIdEnc()),
                edgeIteratorState.get(flagEncoder.getTransfersEnc()), edgeIteratorState.getDistance());
    }

}
