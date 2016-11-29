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

package com.graphhopper.storage;

import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.BreadthFirstSearch;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.Shape;

/**
 * This Class allows to easily Browse the Graph for Edges with Certain Properties
 *
 * TODO: Add Tests for this class (There are integration but no Unit tests)
 * TODO: Maybe this class could be merged into Graph/BaseGraph/Graphstorage?
 *
 * @author Robin Boldt
 */
public class GraphBrowser {

    private final Graph graph;
    private final LocationIndex locationIndex;

    public GraphBrowser(Graph graph, LocationIndex locationIndex) {
        this.graph = graph;
        this.locationIndex = locationIndex;
    }

    public void findClosestEdgeToPoint(GHIntHashSet edgeIds, GHPoint point, EdgeFilter filter) {
        findClosestEdge(edgeIds, point.getLat(), point.getLon(), filter);
    }

    public void findClosestEdge(GHIntHashSet edgeIds, double lat, double lon, EdgeFilter filter) {
        QueryResult qr = locationIndex.findClosest(lat, lon, filter);
        if (qr.isValid())
            edgeIds.add(qr.getClosestEdge().getEdge());
    }

    public void findEdgesInShape(final GHIntHashSet edgeIds, final Shape shape, EdgeFilter filter) {
        // Issue with this is approach is, if there is no street close by, it won't work as qr is not valid
        // Maybe we should check edge points (or random points in the Shape?) if we cannot find a valid edge at the center?
        GHPoint center = shape.getCenter();
        QueryResult qr = locationIndex.findClosest(center.getLat(), center.getLon(), filter);

        // We should throw an Exception here, or add an Error somehow. Otherwise the user will not understand what is happening
        // It might happen that a BBox center does not match an underlying street
        if (!qr.isValid())
            return;

        BreadthFirstSearch bfs = new BreadthFirstSearch() {
            final NodeAccess na = graph.getNodeAccess();
            final Shape localShape = shape;

            @Override
            protected boolean goFurther(int nodeId) {
                return localShape.contains(na.getLatitude(nodeId), na.getLongitude(nodeId));
            }

            @Override
            protected boolean checkAdjacent(EdgeIteratorState edge) {
                if (localShape.contains(na.getLatitude(edge.getAdjNode()), na.getLongitude(edge.getAdjNode()))) {
                    edgeIds.add(edge.getEdge());
                    return true;
                }
                return false;
            }
        };
        bfs.start(graph.createEdgeExplorer(filter), qr.getClosestNode());
    }
}
