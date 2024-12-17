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

package com.graphhopper.reader.osm;

import com.carrotsearch.hppc.IntArrayList;

/**
 * Basically an OSM restriction, but in 'graph-representation', i.e. it is expressed in terms of graph node/edge IDs
 * instead of OSM way IDs. There can be via-node-restrictions (with a single via-node) and via-way/edge-restrictions
 * (with one or more via-edges). There can also be multiple from- or to-edges to represent OSM restrictions like
 * no_entry or no_exit that use multiple from- or to-members.
 * <p>
 * We store a list of via-nodes even for via-way restrictions. It stores the nodes connecting the via-ways,
 * see {@link WayToEdgeConverter.EdgeResult}. For via-node restrictions the list simply contains the single via node.
 * <p>
 * This class only contains the 'topology' of the restriction. The {@link RestrictionType} is handled separately,
 * because opposite to the type the topology does not depend on the vehicle type.
 */
public class RestrictionTopology {
    private final boolean isViaWayRestriction;
    private final IntArrayList viaNodes;
    private final IntArrayList fromEdges;
    private final IntArrayList viaEdges;
    private final IntArrayList toEdges;

    public static RestrictionTopology node(int fromEdge, int viaNode, int toEdge) {
        return node(IntArrayList.from(fromEdge), viaNode, IntArrayList.from(toEdge));
    }

    public static RestrictionTopology node(IntArrayList fromEdges, int viaNode, IntArrayList toEdges) {
        return new RestrictionTopology(false, IntArrayList.from(viaNode), fromEdges, null, toEdges);
    }

    public static RestrictionTopology way(int fromEdge, int viaEdge, int toEdge, IntArrayList viaNodes) {
        return way(fromEdge, IntArrayList.from(viaEdge), toEdge, viaNodes);
    }

    public static RestrictionTopology way(int fromEdge, IntArrayList viaEdges, int toEdge, IntArrayList viaNodes) {
        return way(IntArrayList.from(fromEdge), viaEdges, IntArrayList.from(toEdge), viaNodes);
    }

    public static RestrictionTopology way(IntArrayList fromEdges, IntArrayList viaEdges, IntArrayList toEdges, IntArrayList viaNodes) {
        return new RestrictionTopology(true, viaNodes, fromEdges, viaEdges, toEdges);
    }

    private RestrictionTopology(boolean isViaWayRestriction, IntArrayList viaNodes, IntArrayList fromEdges, IntArrayList viaEdges, IntArrayList toEdges) {
        if (fromEdges.size() > 1 && toEdges.size() > 1)
            throw new IllegalArgumentException("fromEdges and toEdges cannot be size > 1 at the same time");
        if (fromEdges.isEmpty() || toEdges.isEmpty())
            throw new IllegalArgumentException("fromEdges and toEdges must not be empty");
        if (!isViaWayRestriction && viaNodes.size() != 1)
            throw new IllegalArgumentException("for node restrictions there must be exactly one via node");
        if (!isViaWayRestriction && viaEdges != null)
            throw new IllegalArgumentException("for node restrictions the viaEdges must be null");
        if (isViaWayRestriction && viaEdges.isEmpty())
            throw new IllegalArgumentException("for way restrictions there must at least one via edge");
        if (isViaWayRestriction && viaNodes.size() != viaEdges.size() + 1)
            throw new IllegalArgumentException("for way restrictions there must be one via node more than there are via edges");
        this.isViaWayRestriction = isViaWayRestriction;
        this.viaNodes = viaNodes;
        this.fromEdges = fromEdges;
        this.viaEdges = viaEdges;
        this.toEdges = toEdges;
    }

    public boolean isViaWayRestriction() {
        return isViaWayRestriction;
    }

    public IntArrayList getViaNodes() {
        return viaNodes;
    }

    public IntArrayList getFromEdges() {
        return fromEdges;
    }

    public IntArrayList getViaEdges() {
        return viaEdges;
    }

    public IntArrayList getToEdges() {
        return toEdges;
    }
}
