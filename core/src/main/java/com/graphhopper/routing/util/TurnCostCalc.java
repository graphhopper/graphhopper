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
package com.graphhopper.routing.util;

import com.carrotsearch.hppc.DoubleArrayList;
import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;

import static com.graphhopper.util.AngleCalc.ANGLE_CALC;

public class TurnCostCalc {
    // further info: road class difference, car/foot/bike access, angle change, wait time, (bigger road class cross -> not necessary due to wait time?)
    // use cases: turn costs to avoid costly or dangerous left turns or certain angle depending on the road_class (the bigger the road_class the larger the turn can be)
    // TODO NOW: store angles for all edges of a junction => turn costs can be roughly estimated
    // store angles and junction type including road_class etc => deep analysis possible, e.g. some "straight" turns are not without costs as different road_class
    // TODO: which edges to include? all edges of a transportation like bike AND car access?
    // TODO: move these methods to TurnCost class?

    public static class JunctionInfo {
        private final DoubleArrayList orientations = new DoubleArrayList(6);
        // TODO: should we store orientation via edge keys?
        private final IntArrayList edges = new IntArrayList(6);
        private boolean allEdgesNoName;
    }

    public static JunctionInfo calcJunctionInfo(EdgeExplorer explorer, NodeAccess nodeAccess, int node) {
        EdgeIterator edgeIter = explorer.setBaseNode(node);
        double baseLat = nodeAccess.getLat(node), baseLon = nodeAccess.getLon(node);
        JunctionInfo info = new JunctionInfo();
        info.allEdgesNoName = true;

        while (edgeIter.next()) {
            if (!edgeIter.getName().isEmpty()) info.allEdgesNoName = false;
            PointList list = edgeIter.fetchWayGeometry(FetchMode.ALL);
            double lat = list.getLat(1), lon = list.getLon(1);
            // angle in [-pi, +pi] where 0 is east
            double angle = ANGLE_CALC.calcOrientation(baseLat, baseLon, lat, lon);

            info.edges.add(edgeIter.getEdge());
            info.orientations.add(angle);
        }
        return info;
    }

    public static double calcTurnCost180(EdgeExplorer explorer, NodeAccess nodeAccess, int inEdge, int viaNode, int outEdge) {
        double angle = calcTurnCost(explorer, nodeAccess, inEdge, viaNode, outEdge);
        return angle / Math.PI * 180;
    }

    public static double calcTurnCost(EdgeExplorer explorer, NodeAccess nodeAccess, int inEdge, int viaNode, int outEdge) {
        // TODO or is storing + using an array faster?
        JunctionInfo info = calcJunctionInfo(explorer, nodeAccess, viaNode);
        double inOrient = Double.NaN, outOrient = Double.NaN;
        for (int i = 0; i < info.edges.size(); i++) {
            double orientation = info.orientations.get(i);
            int edgeId = info.edges.get(i);
            if (edgeId == inEdge) inOrient = orientation;
            if (edgeId == outEdge) outOrient = orientation;
        }

        if (Double.isNaN(inOrient) || Double.isNaN(outOrient))
            throw new IllegalArgumentException("JunctionInfo does not contain in edge " + inEdge + " nor out:" + outEdge);

        return deltaOrientation(inOrient, outOrient);
    }

    static double deltaOrientation(double inOrient, double outOrient) {
        // orientation was calculated from base towards adjacent node and we need the opposite
        double inAngle = Math.PI + inOrient;
        return ANGLE_CALC.alignOrientation(inAngle, outOrient) - inAngle;
    }

    // TODO turn cost with 2 bits: 0, 1 (right), 2 (left), 3 (maximum is treated as infinity, see TurnCost.create)
    public static int initGraph(GraphHopperStorage storage, FlagEncoder encoder) {
        int addedCosts = 0;
        EncodingManager encodingManager = storage.getEncodingManager();
        TurnCostStorage tcStorage = storage.getTurnCostStorage();
        DecimalEncodedValue turnCostEnc = encodingManager.getDecimalEncodedValue(TurnCost.key(encoder.toString()));
        EdgeExplorer explorer = storage.createEdgeExplorer();
        NodeAccess nodeAccess = storage.getNodeAccess();
        for (int nodeIdx = 0; nodeIdx < storage.getNodes(); nodeIdx++) {
            JunctionInfo info = calcJunctionInfo(explorer, nodeAccess, nodeIdx);

            // do not add costs for unimportant edges
            if (info.allEdgesNoName) continue;

            // TODO NOW restrict access via Weighting
            // TODO NOW create much more efficient storage format e.g. just list of edges + orientations
            for (int fromIdx = 0; fromIdx < info.edges.size(); fromIdx++) {
                int fromEdge = info.edges.get(fromIdx);
                double inOrient = info.orientations.get(fromIdx);
                for (int toIdx = 0; toIdx < info.edges.size(); toIdx++) {
                    if (fromIdx == toIdx)
                        continue; // u-turns are handled elsewhere


                    int toEdge = info.edges.get(toIdx);
                    if (storage.getEdgeIteratorState(fromEdge, nodeIdx).getName().startsWith("Innstr")
                            && storage.getEdgeIteratorState(toEdge, nodeIdx).getName().startsWith("Sonnenallee")) {

                        // http://localhost:8989/maps/?point=52.482205%2C13.444519&point=52.48444%2C13.434992
                        // from north - right turn - 147208: (52.4807051,13.4431265), (52.4804364,13.4427754) TO 48313: (52.4810961,13.4414439), (52.4804364,13.4427754)
                        // from south - left  turn -  48304: (52.4803543,13.4426655), (52.4804364,13.4427754) TO 48313: (52.4810961,13.4414439), (52.4804364,13.4427754)

                        System.out.println(fromEdge + ": " + storage.getEdgeIteratorState(fromEdge, nodeIdx).fetchWayGeometry(FetchMode.ALL)
                                + " TO " + toEdge + ": " + storage.getEdgeIteratorState(toEdge, nodeIdx).fetchWayGeometry(FetchMode.ALL));
                    }

                    double outOrient = info.orientations.get(toIdx);
                    double angle = deltaOrientation(inOrient, outOrient) / Math.PI * 180;
                    if (Math.abs(angle) < 35)
                        continue; // do not add cost for straight direction

                    addedCosts++;
                    // TODO special requirements to DecimalEncodedValue: factor 5 and useMaximumAsInfinity=true
                    if (angle < 0) tcStorage.set(turnCostEnc, fromEdge, nodeIdx, toEdge, 10); // right
                    else tcStorage.set(turnCostEnc, fromEdge, nodeIdx, toEdge, 20);  // left
                }
            }
        }
        tcStorage.flush(); // tc data itself
        storage.flush(); // refs to tc data if new
        return addedCosts;
    }
}
