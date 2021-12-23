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
package com.graphhopper.gtfs;

import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIteratorState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Label {

    static class Transition {
        final Label label;
        final GraphExplorer.MultiModalEdge edge;

        Transition(Label label, GraphExplorer.MultiModalEdge edge) {
            this.label = label;
            this.edge = edge;
        }

        @Override
        public String toString() {
            return (edge != null ? edge.toString() + " -> " : "") + label.node;
        }

    }

    public static class EdgeLabel {
        public final EdgeIteratorState edgeIteratorState;
        public final GtfsStorage.EdgeType edgeType;
        public final String feedId;
        public final int nTransfers;
        public final double distance;
        public PtGraph.PtEdge ptEdge;

        public EdgeLabel(EdgeIteratorState edgeIteratorState, GtfsStorage.EdgeType edgeType, String feedId, int nTransfers, double distance) {
            this.edgeIteratorState = edgeIteratorState;
            this.edgeType = edgeType;
            this.feedId = feedId;
            this.nTransfers = nTransfers;
            this.distance = distance;
        }

        @Override
        public String toString() {
            return edgeType.toString();
        }
    }

    public boolean deleted = false;

    public final long currentTime;

    public final GraphExplorer.MultiModalEdge edge;
    public final NodeId node;

    public final int nTransfers;

    public final Long departureTime;
    public final long streetTime;
    public final long extraWeight;


    final long residualDelay;
    final boolean impossible;

    public final Label parent;

    Label(long currentTime, GraphExplorer.MultiModalEdge edgeId, NodeId node, int nTransfers, Long departureTime, long streetTime, long extraWeight, long residualDelay, boolean impossible, Label parent) {
        this.currentTime = currentTime;
        this.edge = edgeId;
        this.node = node;
        this.nTransfers = nTransfers;
        this.departureTime = departureTime;
        this.streetTime = streetTime;
        this.extraWeight = extraWeight;
        this.residualDelay = residualDelay;
        this.impossible = impossible;
        this.parent = parent;
    }

    @Override
    public String toString() {
        return node + " " + (departureTime != null ? Instant.ofEpochMilli(departureTime) : "---") + "\t" + nTransfers + "\t" + Instant.ofEpochMilli(currentTime);
    }

    static List<Label.Transition> getTransitions(Label _label, boolean arriveBy, Graph queryGraph, PtGraph ptGraph, RealtimeFeed realtimeFeed) {
        Label label = _label;
        boolean reverseEdgeFlags = !arriveBy;
        List<Label.Transition> result = new ArrayList<>();
        if (!reverseEdgeFlags) {
            result.add(new Label.Transition(label, null));
        }
        while (label.parent != null) {
//            EdgeIteratorState edgeIteratorState = queryGraph.getEdgeIteratorState(label.edge, reverseEdgeFlags ? label.adjNode : label.parent.adjNode).detach(false);
//            if (reverseEdgeFlags && edgeIteratorState != null && (edgeIteratorState.getBaseNode() != label.parent.adjNode || edgeIteratorState.getAdjNode() != label.adjNode)) {
//                throw new IllegalStateException();
//            }
//            if (!reverseEdgeFlags && edgeIteratorState != null && (edgeIteratorState.getAdjNode() != label.parent.adjNode || edgeIteratorState.getBaseNode() != label.adjNode)) {
//                throw new IllegalStateException();
//            }
            Label.Transition transition;
            if (reverseEdgeFlags) {
                transition = new Label.Transition(label, label.edge);
            } else {
                transition = new Label.Transition(label.parent, label.edge);
            }
            label = label.parent;
            result.add(transition);
        }
        if (reverseEdgeFlags) {
            result.add(new Label.Transition(label, null));
            Collections.reverse(result);
        }
        return result;
    }

    public static EdgeLabel getEdgeLabel(PtGraph.PtEdge ptEdge, RealtimeFeed realtimeFeed) {
        GtfsStorage.EdgeType edgeType = ptEdge.getAttrs().type;
        String feedId;
        if (edgeType == GtfsStorage.EdgeType.ENTER_PT || edgeType == GtfsStorage.EdgeType.TRANSFER) {
            GtfsStorageI.PlatformDescriptor platformDescriptor = realtimeFeed.getPlatformDescriptorByEdge().get(ptEdge.getId());
            feedId = platformDescriptor.feed_id;
        } else {
            feedId = null;
        }
        int nTransfers = ptEdge.getAttrs().transfers;
        double distance = 1234; // FIXME
        EdgeLabel edgeLabel = new EdgeLabel(null, edgeType, feedId, nTransfers, distance);
        edgeLabel.ptEdge = ptEdge;
        return edgeLabel;
    }

    public static EdgeLabel getEdgeLabel(EdgeIteratorState edgeIteratorState) {
        double distance = edgeIteratorState.getDistance();
        return new EdgeLabel(edgeIteratorState, GtfsStorage.EdgeType.HIGHWAY, null, 0, distance);
    }

    public static class NodeId {
        public NodeId(int node, boolean pt) {
            this.node = node;
            this.pt = pt;
        }

        public int node;
        public boolean pt;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NodeId nodeId = (NodeId) o;
            return node == nodeId.node && pt == nodeId.pt;
        }

        @Override
        public int hashCode() {
            return Objects.hash(node, pt);
        }

        @Override
        public String toString() {
            return "NodeId{" +
                    "node=" + node +
                    ", pt=" + pt +
                    '}';
        }
    }
}
