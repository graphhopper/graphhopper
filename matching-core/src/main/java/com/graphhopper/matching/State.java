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
package com.graphhopper.matching;

import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIteratorState;

/**
 * During map matching this represents a map matching candidate, i.e. a potential snapped
 * point of a GPX entry. After map matching, this represents the map matched point of
 * an GPX entry.
 * <p>
 * A Observation can either be at an undirected real (tower) node or at a directed virtual node.
 * If this is at a directed virtual node then incoming paths from any previous State
 * should arrive through {@link #getIncomingVirtualEdge()} and outgoing paths to any following
 * State should start with {@link #getOutgoingVirtualEdge()}. This is achieved by
 * penalizing other edges for routing. Note that virtual nodes are always connected to their
 * adjacent nodes via 2 virtual edges (not counting reverse virtual edges).
 *
 * @author Peter Karich
 * @author kodonnell
 * @author Stefan Holder
 */
public class State {
    private final Observation entry;
    private final Snap snap;
    private final boolean isDirected;
    private final EdgeIteratorState incomingVirtualEdge;
    private final EdgeIteratorState outgoingVirtualEdge;

    /**
     * Creates an undirected candidate for a real node.
     */
    public State(Observation entry, Snap snap) {
        this.entry = entry;
        this.snap = snap;
        this.isDirected = false;
        this.incomingVirtualEdge = null;
        this.outgoingVirtualEdge = null;
    }

    /**
     * Creates a directed candidate for a virtual node.
     */
    public State(Observation entry, Snap snap,
                 VirtualEdgeIteratorState incomingVirtualEdge,
                 VirtualEdgeIteratorState outgoingVirtualEdge) {
        this.entry = entry;
        this.snap = snap;
        this.isDirected = true;
        this.incomingVirtualEdge = incomingVirtualEdge;
        this.outgoingVirtualEdge = outgoingVirtualEdge;
    }

    public Observation getEntry() {
        return entry;
    }

    public Snap getSnap() {
        return snap;
    }

    /**
     * Returns whether this State is directed. This is true if the snapped point
     * is a virtual node, otherwise the snapped node is a real (tower) node and false is returned.
     */
    public boolean isOnDirectedEdge() {
        return isDirected;
    }

    /**
     * Returns the virtual edge that should be used by incoming paths.
     *
     * @throws IllegalStateException if this State is not directed.
     */
    public EdgeIteratorState getIncomingVirtualEdge() {
        if (!isDirected) {
            throw new IllegalStateException(
                    "This method may only be called for directed GPXExtensions");
        }
        return incomingVirtualEdge;
    }

    /**
     * Returns the virtual edge that should be used by outgoing paths.
     *
     * @throws IllegalStateException if this State is not directed.
     */
    public EdgeIteratorState getOutgoingVirtualEdge() {
        if (!isDirected) {
            throw new IllegalStateException(
                    "This method may only be called for directed GPXExtensions");
        }
        return outgoingVirtualEdge;
    }

    @Override
    public String toString() {
        return "State{" +
                "closest node=" + snap.getClosestNode() +
                " at " + snap.getSnappedPoint().getLat() + "," +
                snap.getSnappedPoint().getLon() +
                ", incomingEdge=" + incomingVirtualEdge +
                ", outgoingEdge=" + outgoingVirtualEdge +
                '}';
    }
}