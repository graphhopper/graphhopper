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
package com.graphhopper.util.details;

import com.graphhopper.core.util.PointList;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;

import java.util.*;

import static com.graphhopper.core.util.Parameters.Details.INTERSECTION;
import com.graphhopper.util.AngleCalc;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.GHUtility;

/**
 * Calculate the intersections for a route. Every change of the edge id is considered an intersection.
 * <p>
 * The format is inspired by the format that is consumed by Maplibre Navigation SDK.
 * <p>
 * Explanation of the format:
 * - <code>entries</code> contain an array of the edges at that intersection. They are sorted by bearing, starting from 0 (which is 0° north) to 359. Every edge that we can turn onto is marked with “true” in the array.
 * - <code>bearings</code> contain an array of the edges at that intersection. They are sorted by bearing, starting from 0 (which is 0° north) to 359.  The array contains the bearings of each edge at that intersection.
 * - <code>in</code> marks the index in the “bearings” edge we are coming from.
 * - <code>out</code> the index we are going to.
 *
 * @author Robin Boldt
 */
public class IntersectionDetails extends AbstractPathDetailsBuilder {

    private int fromEdge = -1;

    private Map<String, Object> intersectionMap = new HashMap<>();

    private final EdgeExplorer crossingExplorer;
    private final NodeAccess nodeAccess;
    private final Weighting weighting;

    public IntersectionDetails(Graph graph, Weighting weighting) {
        super(INTERSECTION);

        crossingExplorer = graph.createEdgeExplorer();
        nodeAccess = graph.getNodeAccess();
        this.weighting = weighting;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        int toEdge = edgeId(edge);
        if (toEdge != fromEdge) {
            // Important to create a new map and not to clean the old map!
            intersectionMap = new HashMap<>();

            List<IntersectionValues> intersectingEdges = new ArrayList<>();

            int baseNode = edge.getBaseNode();
            EdgeIteratorState tmpEdge;

            double startLat = nodeAccess.getLat(baseNode);
            double startLon = nodeAccess.getLon(baseNode);


            EdgeIterator edgeIter = crossingExplorer.setBaseNode(baseNode);
            while (edgeIter.next()) {
                // We need to call detach to get the edgeId, as we need to check for VirtualEdgeIteratorState in #edgeId(), see discussion in #2590
                tmpEdge = edgeIter.detach(false);

                IntersectionValues intersectionValues = new IntersectionValues();
                intersectionValues.bearing = calculateBearing(startLat, startLon, tmpEdge);
                intersectionValues.in = edgeId(tmpEdge) == fromEdge;
                intersectionValues.out = edgeId(tmpEdge) == edgeId(edge);
                // The in edge is always false, this means that u-turns are not considered as possible turning option
                intersectionValues.entry = !intersectionValues.in && Double.isFinite(weighting.calcEdgeWeightWithAccess(tmpEdge, false));

                intersectingEdges.add(intersectionValues);
            }

            intersectingEdges.sort(null);

            List<Integer> bearings = new ArrayList<>(intersectingEdges.size());
            List<Boolean> entries = new ArrayList<>(intersectingEdges.size());

            for (int i = 0; i < intersectingEdges.size(); i++) {
                IntersectionValues intersectionValues = intersectingEdges.get(i);
                bearings.add(intersectionValues.bearing);
                entries.add(intersectionValues.entry);
                if (intersectionValues.in) {
                    intersectionMap.put("in", i);
                }
                if (intersectionValues.out) {
                    intersectionMap.put("out", i);
                }
            }

            intersectionMap.put("bearings", bearings);
            intersectionMap.put("entries", entries);

            fromEdge = toEdge;
            return true;
        }
        return false;
    }

    private int calculateBearing(double startLat, double startLon, EdgeIteratorState tmpEdge) {
        PointList wayGeo = tmpEdge.fetchWayGeometry(FetchMode.PILLAR_AND_ADJ);
        final double latitude = wayGeo.getLat(0);
        final double longitude = wayGeo.getLon(0);
        return (int) Math.round(AngleCalc.ANGLE_CALC.calcAzimuth(startLat, startLon, latitude, longitude));
    }

    private int edgeId(EdgeIteratorState edge) {
        if (edge instanceof VirtualEdgeIteratorState) {
            return GHUtility.getEdgeFromEdgeKey(((VirtualEdgeIteratorState) edge).getOriginalEdgeKey());
        } else {
            return edge.getEdge();
        }
    }

    @Override
    public Object getCurrentValue() {
        return this.intersectionMap;
    }

    private class IntersectionValues implements Comparable {

        public int bearing;
        public boolean entry;
        public boolean in;
        public boolean out;

        @Override
        public int compareTo(Object o) {
            if (o instanceof IntersectionValues) {
                return Integer.compare(this.bearing, ((IntersectionValues) o).bearing);
            }
            return 0;
        }
    }
}