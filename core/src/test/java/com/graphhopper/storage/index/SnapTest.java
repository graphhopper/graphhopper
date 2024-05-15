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

package com.graphhopper.storage.index;

import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import static com.graphhopper.util.DistancePlaneProjection.DIST_PLANE;
import static org.junit.jupiter.api.Assertions.*;

class SnapTest {

    @Test
    void snapToCloseTower() {
        // see #???
        BaseGraph graph = new BaseGraph.Builder(1).create();
        EdgeIteratorState edge = graph.edge(0, 1);
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 40.000_000, 6.000_000);
        na.setNode(1, 40.000_000, 6.000_101);
        double queryLat = 40.001_000;
        double queryLon = 6.000_1009;
        Snap snap = new Snap(queryLat, queryLon);
        snap.setClosestEdge(edge);
        // We set the base node to the closest node, even though the crossing point is closer to
        // the adj node. Not sure if LocationIndexTree can really produce this situation.
        snap.setClosestNode(edge.getBaseNode());
        snap.setWayIndex(0);
        snap.setSnappedPosition(Snap.Position.EDGE);
        // the crossing point is very close to the adj node
        GHPoint crossingPoint = DIST_PLANE.calcCrossingPointToEdge(queryLat, queryLon,
                na.getLat(edge.getBaseNode()), na.getLon(edge.getBaseNode()), na.getLat(edge.getAdjNode()), na.getLon(edge.getAdjNode()));
        double distCrossingTo0 = DIST_PLANE.calcDist(crossingPoint.lat, crossingPoint.lon, na.getLat(edge.getBaseNode()), na.getLon(edge.getBaseNode()));
        double distCrossingTo1 = DIST_PLANE.calcDist(crossingPoint.lat, crossingPoint.lon, na.getLat(edge.getAdjNode()), na.getLon(edge.getAdjNode()));
        assertEquals(8.594, distCrossingTo0, 1.e-3);
        assertEquals(0.008, distCrossingTo1, 1.e-3);
        // the snapped point snaps to the adj tower node, so the coordinates must the same
        snap.calcSnappedPoint(DIST_PLANE);
        assertEquals(na.getLat(snap.getClosestNode()), snap.getSnappedPoint().getLat());
        assertEquals(na.getLon(snap.getClosestNode()), snap.getSnappedPoint().getLon());
        assertEquals(edge.getAdjNode(), snap.getClosestNode());
    }

}
