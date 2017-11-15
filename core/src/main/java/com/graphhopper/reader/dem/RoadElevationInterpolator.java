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
package com.graphhopper.reader.dem;

import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;

/**
 * This class smooths the elevation data of all edges by calculating the average elevation over
 * multiple points of that edge.
 * <p>
 * The ElevationData is read from rectangular tiles. Especially when going along a cliff,
 * valley, or pass, it can happen that a small part of the road contains incorrect elevation data.
 * This is because the elevation data can is coarse and sometimes contains errors.
 *
 * @author Robin Boldt
 */
public class RoadElevationInterpolator {

    private final static int MIN_GEOMETRY_SIZE = 3;
    // The max amount of points to go left and right
    private final static int MAX_SEARCH_RADIUS = 8;
    // If the point is farther then this, we stop averaging
    private final static int MAX_SEARCH_DISTANCE = 180;


    public static void smoothElevation(Graph graph) {
        final AllEdgesIterator edge = graph.getAllEdges();
        final GHBitSet visitedEdgeIds = new GHBitSetImpl(edge.getMaxId());

        while (edge.next()) {
            final int edgeId = edge.getEdge();
            if (!visitedEdgeIds.contains(edgeId)) {
                PointList geometry = edge.fetchWayGeometry(3);
                if (geometry.size() >= MIN_GEOMETRY_SIZE) {
                    for (int i = 1; i < geometry.size() - 1; i++) {
                        int start = i - MAX_SEARCH_RADIUS < 0 ? 0 : i - MAX_SEARCH_RADIUS;
                        // +1 because we check for "< end"
                        int end = i + MAX_SEARCH_RADIUS + 1 >= geometry.size() ? geometry.size() : i + MAX_SEARCH_RADIUS + 1;
                        double sum = 0;
                        int counter = 0;
                        for (int j = start; j < end; j++) {
                            // We skip points that are too far away, important for motorways
                            if (MAX_SEARCH_DISTANCE > Helper.DIST_PLANE.calcDist(geometry.getLat(i), geometry.getLon(i), geometry.getLat(j), geometry.getLon(j))) {
                                sum += geometry.getEle(j);
                                counter++;
                            }
                        }
                        double smoothed = sum / counter;
                        geometry.setElevation(i, smoothed);
                    }
                    //Remove the Tower Nodes
                    PointList pillarNodesPointList = geometry.copy(1, geometry.size()-1);
                    edge.setWayGeometry(pillarNodesPointList);
                }
            }
            visitedEdgeIds.add(edgeId);
        }
    }

}
