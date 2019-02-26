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

import com.carrotsearch.hppc.IntSet;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;

/**
 * Abstract base class for tunnel/bridge edge elevation interpolators. This
 * class estimates elevation of inner nodes of a tunnel/bridge based on
 * elevations of entry nodes. See #713 for more information.
 * <p>
 * Since inner nodes of tunnel or bridge do not lie on the Earth surface, we
 * should not use elevations returned by the elevation provider for these
 * points. Instead, we'll estimate elevations of these points based on
 * elevations of entry/exit nodes of the tunnel/bridge.
 * <p>
 * To do this, we'll iterate over the graph looking for tunnel or bridge edges
 * using {@link #isInterpolatableEdge(EdgeIteratorState)}. Once such an edge is
 * found, we'll calculate a connected component of tunnel/bridge edges starting
 * from the base node of this edge, using simple {@link BreadthFirstSearch}.
 * Nodes which only have interpolatabe edges connected to them are inner nodes
 * and are considered to not lie on the Earth surface. Nodes which also have
 * non-interpolatable edges are outer nodes and are considered to lie on the
 * Earth surface. Elevations of inner nodes are then interpolated from the outer
 * nodes using {@link NodeElevationInterpolator}. Elevations of pillar nodes are
 * calculated using linear interpolation on distances from tower nodes.
 *
 * @author Alexey Valikov
 */
public abstract class AbstractEdgeElevationInterpolator {

    private final GraphHopperStorage storage;
    protected final DataFlagEncoder dataFlagEncoder;
    private final NodeElevationInterpolator nodeElevationInterpolator;
    private final ElevationInterpolator elevationInterpolator = new ElevationInterpolator();

    public AbstractEdgeElevationInterpolator(GraphHopperStorage storage,
                                             DataFlagEncoder dataFlagEncoder) {
        this.storage = storage;
        this.dataFlagEncoder = dataFlagEncoder;
        this.nodeElevationInterpolator = new NodeElevationInterpolator(storage);
    }

    protected abstract boolean isInterpolatableEdge(EdgeIteratorState edge);

    public GraphHopperStorage getStorage() {
        return storage;
    }

    public void execute() {
        interpolateElevationsOfTowerNodes();
        interpolateElevationsOfPillarNodes();
    }

    private void interpolateElevationsOfTowerNodes() {
        final AllEdgesIterator edge = storage.getAllEdges();
        final GHBitSet visitedEdgeIds = new GHBitSetImpl(edge.length());
        final EdgeExplorer edgeExplorer = storage.createEdgeExplorer();

        while (edge.next()) {
            final int edgeId = edge.getEdge();
            if (isInterpolatableEdge(edge)) {
                if (!visitedEdgeIds.contains(edgeId)) {
                    interpolateEdge(edge, visitedEdgeIds, edgeExplorer);
                }
            }
            visitedEdgeIds.add(edgeId);
        }
    }

    private void interpolateEdge(final EdgeIteratorState interpolatableEdge,
                                 final GHBitSet visitedEdgeIds, final EdgeExplorer edgeExplorer) {
        final IntSet outerNodeIds = new GHIntHashSet();
        final GHIntHashSet innerNodeIds = new GHIntHashSet();
        gatherOuterAndInnerNodeIds(edgeExplorer, interpolatableEdge, visitedEdgeIds, outerNodeIds,
                innerNodeIds);
        nodeElevationInterpolator.interpolateElevationsOfInnerNodes(outerNodeIds.toArray(),
                innerNodeIds.toArray());
    }

    public void gatherOuterAndInnerNodeIds(final EdgeExplorer edgeExplorer,
                                           final EdgeIteratorState interpolatableEdge, final GHBitSet visitedEdgesIds,
                                           final IntSet outerNodeIds, final GHIntHashSet innerNodeIds) {
        final BreadthFirstSearch gatherOuterAndInnerNodeIdsSearch = new BreadthFirstSearch() {
            @Override
            protected boolean checkAdjacent(EdgeIteratorState edge) {
                visitedEdgesIds.add(edge.getEdge());
                final int baseNodeId = edge.getBaseNode();
                boolean isInterpolatableEdge = isInterpolatableEdge(edge);
                if (!isInterpolatableEdge) {
                    innerNodeIds.remove(baseNodeId);
                    outerNodeIds.add(baseNodeId);
                } else if (!outerNodeIds.contains(baseNodeId)) {
                    innerNodeIds.add(baseNodeId);
                }
                return isInterpolatableEdge;
            }
        };
        gatherOuterAndInnerNodeIdsSearch.start(edgeExplorer, interpolatableEdge.getBaseNode());
    }

    private void interpolateElevationsOfPillarNodes() {
        final EdgeIterator edge = storage.getAllEdges();
        final NodeAccess nodeAccess = storage.getNodeAccess();
        while (edge.next()) {
            if (isInterpolatableEdge(edge)) {
                int firstNodeId = edge.getBaseNode();
                int secondNodeId = edge.getAdjNode();

                double lat0 = nodeAccess.getLat(firstNodeId);
                double lon0 = nodeAccess.getLon(firstNodeId);
                double ele0 = nodeAccess.getEle(firstNodeId);

                double lat1 = nodeAccess.getLat(secondNodeId);
                double lon1 = nodeAccess.getLon(secondNodeId);
                double ele1 = nodeAccess.getEle(secondNodeId);

                final PointList pointList = edge.fetchWayGeometry(0);
                final int count = pointList.size();
                for (int index = 0; index < count; index++) {
                    double lat = pointList.getLat(index);
                    double lon = pointList.getLon(index);
                    double ele = elevationInterpolator.calculateElevationBasedOnTwoPoints(lat, lon,
                            lat0, lon0, ele0, lat1, lon1, ele1);
                    pointList.set(index, lat, lon, ele);
                }
                edge.setWayGeometry(pointList);
            }
        }
    }
}
