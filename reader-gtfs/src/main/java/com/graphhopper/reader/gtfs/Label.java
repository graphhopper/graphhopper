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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public static class EdgeLabel {
        public final EdgeIteratorState edgeIteratorState;
        public final GtfsStorage.EdgeType edgeType;
        public final int timeZoneId;
        public final int nTransfers;
        public final double distance;

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

    public final int edge;
    public final int adjNode;

    public final int nTransfers;

    public final double walkDistanceOnCurrentLeg;
    public final Long departureTime;
    public final long walkTime;

    final long residualDelay;
    final boolean impossible;

    public final Label parent;

    Label(long currentTime, int edgeId, int adjNode, int nTransfers, double walkDistance, Long departureTime, long walkTime, long residualDelay, boolean impossible, Label parent) {
        this.currentTime = currentTime;
        this.edge = edgeId;
        this.adjNode = adjNode;
        this.nTransfers = nTransfers;
        this.walkDistanceOnCurrentLeg = walkDistance;
        this.departureTime = departureTime;
        this.walkTime = walkTime;
        this.residualDelay = residualDelay;
        this.impossible = impossible;
        this.parent = parent;
    }

    @Override
    public String toString() {
        return adjNode + " " + (departureTime != null ? Instant.ofEpochMilli(departureTime) : "---") + "\t" + nTransfers + "\t" + Instant.ofEpochMilli(currentTime);
    }

    static List<Label.Transition> getTransitions(Label _label, boolean arriveBy, PtEncodedValues encoder, Graph queryGraph) {
        Label label = _label;
        boolean reverseEdgeFlags = !arriveBy;
        List<Label.Transition> result = new ArrayList<>();
        if (!reverseEdgeFlags) {
            result.add(new Label.Transition(label, null));
        }
        while (label.parent != null) {
            EdgeIteratorState edgeIteratorState = queryGraph.getEdgeIteratorState(label.edge, reverseEdgeFlags ? label.adjNode : label.parent.adjNode).detach(false);
            if (reverseEdgeFlags && edgeIteratorState != null && (edgeIteratorState.getBaseNode() != label.parent.adjNode || edgeIteratorState.getAdjNode() != label.adjNode)) {
                throw new IllegalStateException();
            }
            if (!reverseEdgeFlags && edgeIteratorState != null && (edgeIteratorState.getAdjNode() != label.parent.adjNode || edgeIteratorState.getBaseNode() != label.adjNode)) {
                throw new IllegalStateException();
            }

            Label.Transition transition;
            if (reverseEdgeFlags) {
                transition = new Label.Transition(label, edgeIteratorState != null ? Label.getEdgeLabel(edgeIteratorState, encoder) : null);
            } else {
                transition = new Label.Transition(label.parent, edgeIteratorState != null ? Label.getEdgeLabel(edgeIteratorState, encoder) : null);
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

    public static EdgeLabel getEdgeLabel(EdgeIteratorState edgeIteratorState, PtEncodedValues flagEncoder) {
        return new EdgeLabel(edgeIteratorState, edgeIteratorState.get(flagEncoder.getTypeEnc()), edgeIteratorState.get(flagEncoder.getValidityIdEnc()),
                edgeIteratorState.get(flagEncoder.getTransfersEnc()), edgeIteratorState.getDistance());
    }

    public static void logLabel(GtfsGraphLogger logger, Label label, boolean arriveBy, PtEncodedValues encoder, Graph queryGraph) {

        String logHighwayEdge = System.getenv("GH_GTFS_LOG_HIGHWAY_EDGE_GRAPH_LOGGER");
        boolean reverseEdgeFlags = !arriveBy;

        if (label.edge != -1) {
            EdgeIteratorState edgeIteratorState = queryGraph.getEdgeIteratorState(label.edge, reverseEdgeFlags ? label.adjNode : label.parent.adjNode).detach(false);
            EdgeLabel edgeLabel = Label.getEdgeLabel(edgeIteratorState, encoder);

            String edgeLabelStr = "";
            GtfsGraphLogger.NodeLogType baseNodeType = GtfsGraphLogger.NodeLogType.OSM_NODE;
            GtfsGraphLogger.NodeLogType adjNodeType = GtfsGraphLogger.NodeLogType.OSM_NODE;


            switch (edgeLabel.edgeType) {
                case HIGHWAY:
                    if (null != logHighwayEdge && logHighwayEdge.compareTo("0") == 0) {
                        return;
                    }
                    edgeLabelStr = "HIGHWAY";
                    baseNodeType = GtfsGraphLogger.NodeLogType.OSM_NODE;
                    adjNodeType = GtfsGraphLogger.NodeLogType.OSM_NODE;
                    break;
                case ENTER_TIME_EXPANDED_NETWORK:
                    edgeLabelStr = "ENTER_TEN";
                    baseNodeType = GtfsGraphLogger.NodeLogType.ENTER_EXIT_PT;
                    adjNodeType = GtfsGraphLogger.NodeLogType.BOARD_NODE;
                    break;
                case LEAVE_TIME_EXPANDED_NETWORK:
                    edgeLabelStr = "LEAVE_TEN";
                    baseNodeType = GtfsGraphLogger.NodeLogType.ALIGHT_NODE;
                    adjNodeType = GtfsGraphLogger.NodeLogType.ENTER_EXIT_PT;
                    break;
                case ENTER_PT:
                    edgeLabelStr = "ENTER_PT";
                    baseNodeType = GtfsGraphLogger.NodeLogType.OSM_NODE;
                    adjNodeType = GtfsGraphLogger.NodeLogType.ENTER_EXIT_PT;
                    break;
                case EXIT_PT:
                    edgeLabelStr = "EXIT_PT";
                    baseNodeType = GtfsGraphLogger.NodeLogType.ENTER_EXIT_PT;
                    adjNodeType = GtfsGraphLogger.NodeLogType.OSM_NODE;
                    break;
                case HOP:
                    edgeLabelStr = "HOP";
                    baseNodeType = GtfsGraphLogger.NodeLogType.DEPARTURE_STOP_TIME_NODE;
                    adjNodeType = GtfsGraphLogger.NodeLogType.ARRIVAL_STOP_TIME_NODE;
                    break;
                case DWELL:
                    edgeLabelStr = "DWELL";
                    baseNodeType = GtfsGraphLogger.NodeLogType.ARRIVAL_STOP_TIME_NODE;
                    adjNodeType = GtfsGraphLogger.NodeLogType.DEPARTURE_STOP_TIME_NODE;
                    break;
                case BOARD:
                    edgeLabelStr = "BOARD";
                    baseNodeType = GtfsGraphLogger.NodeLogType.BOARD_NODE;
                    adjNodeType = GtfsGraphLogger.NodeLogType.DEPARTURE_STOP_TIME_NODE;
                    break;
                case ALIGHT:
                    edgeLabelStr = "ALIGHT";
                    baseNodeType = GtfsGraphLogger.NodeLogType.ARRIVAL_STOP_TIME_NODE;
                    adjNodeType = GtfsGraphLogger.NodeLogType.ALIGHT_NODE;
                    logger.incrementTrip();
                    break;
                case OVERNIGHT:
                    edgeLabelStr = "OVERNIGHT";
                    baseNodeType = GtfsGraphLogger.NodeLogType.BOARD_NODE;
                    adjNodeType = GtfsGraphLogger.NodeLogType.BOARD_NODE;
                    break;
                case TRANSFER:
                    edgeLabelStr = "TRANSFER";
                    baseNodeType = GtfsGraphLogger.NodeLogType.ALIGHT_NODE;
                    adjNodeType = GtfsGraphLogger.NodeLogType.BOARD_NODE;
                    break;
                case WAIT:
                    edgeLabelStr = "WAIT";
                    break;
                case WAIT_ARRIVAL:
                    edgeLabelStr = "WAIT_ARRIVAL";
                    break;
            }

            logger.addNode(edgeIteratorState.getBaseNode(), 0, 0, baseNodeType, "");
            logger.addNode(edgeIteratorState.getAdjNode(), 0, 0, adjNodeType, "");
            logger.addEdge(edgeLabelStr, edgeIteratorState.getEdge(), edgeIteratorState.getBaseNode(), edgeIteratorState.getAdjNode());
        }
        else {
            logger.addNode(label.adjNode, 0, 0, GtfsGraphLogger.NodeLogType.OSM_NODE, "");
        }
    }
}
