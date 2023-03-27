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

import com.carrotsearch.hppc.IntHashSet;

import java.time.Instant;
import java.util.*;

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

    public final List<String> blockedPatterns = new ArrayList<>();

    public final Label parent;

    Label(long currentTime, GraphExplorer.MultiModalEdge edge, NodeId node, int nTransfers, Long departureTime, long streetTime, long extraWeight, long residualDelay, boolean impossible, Label parent) {
        this.currentTime = currentTime;
        this.edge = edge;
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

    static List<Label.Transition> getTransitions(Label _label, boolean arriveBy) {
        Label label = _label;
        boolean reverseEdgeFlags = !arriveBy;
        List<Label.Transition> result = new ArrayList<>();
        if (!reverseEdgeFlags) {
            result.add(new Label.Transition(label, null));
        }
        while (label.parent != null) {
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

    public static class NodeId {
        public NodeId(int streetNode, int ptNode) {
            this.streetNode = streetNode;
            this.ptNode = ptNode;
        }

        public int streetNode;
        public int ptNode;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NodeId nodeId = (NodeId) o;
            return streetNode == nodeId.streetNode && ptNode == nodeId.ptNode;
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + streetNode;
            result = 31 * result + ptNode;
            return result;
        }

        @Override
        public String toString() {
            return "NodeId{" +
                    "streetNode=" + streetNode +
                    ", ptNode=" + ptNode +
                    '}';
        }
    }
}
