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
package com.graphhopper.util;

import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.IntsRef;

/**
 * This interface represents an edge and is one possible state of an EdgeIterator.
 * Example:
 * <span>
 * EdgeExplorer expl = graph.createEdgeExplorer();
 * EdgeIterator iter = expl.setBaseNode(baseNode);
 * while(iter.next()) {
 * iter.getBaseBase() // equals to the specified baseNode
 * }
 * </span>
 *
 * @author Peter Karich
 * @see EdgeIterator
 * @see EdgeExplorer
 */
public interface EdgeIteratorState {
    BooleanEncodedValue UNFAVORED_EDGE = new SimpleBooleanEncodedValue("unfavored");
    /**
     * This method can be used to fetch the internal reverse state of an edge.
     */
    BooleanEncodedValue REVERSE_STATE = new BooleanEncodedValue() {
        @Override
        public int init(InitializerConfig init) {
            throw new IllegalStateException("Cannot happen for this BooleanEncodedValue");
        }

        @Override
        public String getName() {
            return "reverse";
        }

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public boolean getBool(boolean reverse, IntsRef ref) {
            return reverse;
        }

        @Override
        public void setBool(boolean reverse, IntsRef ref, boolean value) {
            throw new IllegalStateException("reverse state cannot be modified");
        }

        @Override
        public boolean isStoreTwoDirections() {
            return false;
        }
    };

    boolean isReversed();

    /**
     * @return the edge id of the current edge. Do not make any assumptions about the concrete
     * values, except that for an implementation it is recommended that they'll be contiguous.
     */
    int getEdge();

    /**
     * Returns the edge key of the current edge. The edge id can be derived from the edge key by calling
     * {@link GHUtility#getEdgeFromEdgeKey(int)}, but the edge key also contains information about the
     * direction of the edge. The edge key is even when the edge is oriented in storage direction and odd
     * otherwise. You can use the edge key to retrieve an edge state in the associated direction using
     * {@link Graph#getEdgeIteratorStateForKey(int)}. Loop edges are always returned in 'forward' direction even when
     * you use an odd edge key.
     */
    int getEdgeKey();

    /**
     * @return the edge id of the first original edge of the current edge. This is needed for shortcuts
     * in edge-based contraction hierarchies and otherwise simply returns the id of the current edge.
     */
    int getOrigEdgeFirst();

    /**
     * @see #getOrigEdgeFirst()
     */
    int getOrigEdgeLast();

    /**
     * Returns the node used to instantiate the EdgeIterator. Often only used for convenience reasons.
     * Do not confuse this with a <i>source node</i> of a directed edge.
     *
     * @return the requested node itself
     * @see EdgeIterator
     */
    int getBaseNode();

    /**
     * @return the adjacent node of baseNode for the current edge.
     * @see EdgeIterator
     */
    int getAdjNode();

    /**
     * For road network data like OSM a way is often not just a straight line. The nodes between the junction nodes
     * are called pillar nodes. The junction nodes are called tower nodes and used for routing. The pillar nodes are
     * necessary to have an exact geometry. See the docs for more information
     * (docs/core/low-level-api.md#what-are-pillar-and-tower-nodes). Updates to the returned list
     * are not reflected in the graph, for that you've to use setWayGeometry.
     *
     * @param mode {@link FetchMode}
     * @return the pillar and/or tower nodes depending on the mode.
     */
    PointList fetchWayGeometry(FetchMode mode);

    /**
     * @param list is a sorted collection of coordinates between the base node and the current adjacent node. Specify
     *             the list without the adjacent and base node. This method can be called multiple times, but if the
     *             distance changes, the setDistance method is not called automatically.
     */
    EdgeIteratorState setWayGeometry(PointList list);

    /**
     * @return the distance of the current edge in meter
     */
    double getDistance();

    /**
     * @return the grade of the current edge in %
     */
    int getGrade();

    EdgeIteratorState setDistance(double dist);

    EdgeIteratorState setGrade(int grade);

    /**
     * Returns edge properties stored in direction of the raw database layout. So do not use it directly, instead
     * use the appropriate set/get methods with its EncodedValue object.
     */
    IntsRef getFlags();

    /**
     * Stores the specified edgeFlags down to the DataAccess
     */
    EdgeIteratorState setFlags(IntsRef edgeFlags);

    boolean get(BooleanEncodedValue property);

    EdgeIteratorState set(BooleanEncodedValue property, boolean value);

    boolean getReverse(BooleanEncodedValue property);

    EdgeIteratorState setReverse(BooleanEncodedValue property, boolean value);

    EdgeIteratorState set(BooleanEncodedValue property, boolean fwd, boolean bwd);

    int get(IntEncodedValue property);

    EdgeIteratorState set(IntEncodedValue property, int value);

    int getReverse(IntEncodedValue property);

    EdgeIteratorState setReverse(IntEncodedValue property, int value);

    EdgeIteratorState set(IntEncodedValue property, int fwd, int bwd);

    double get(DecimalEncodedValue property);

    EdgeIteratorState set(DecimalEncodedValue property, double value);

    double getReverse(DecimalEncodedValue property);

    EdgeIteratorState setReverse(DecimalEncodedValue property, double value);

    EdgeIteratorState set(DecimalEncodedValue property, double fwd, double bwd);

    <T extends Enum<?>> T get(EnumEncodedValue<T> property);

    <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T value);

    <T extends Enum<?>> T getReverse(EnumEncodedValue<T> property);

    <T extends Enum<?>> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value);

    <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T fwd, T bwd);
    
    String get(StringEncodedValue property);
    
    EdgeIteratorState set(StringEncodedValue property, String value);
    
    String getReverse(StringEncodedValue property);
    
    EdgeIteratorState setReverse(StringEncodedValue property, String value);
    
    EdgeIteratorState set(StringEncodedValue property, String fwd, String bwd);

    String getName();

    EdgeIteratorState setName(String name);

    /**
     * Clones this EdgeIteratorState.
     *
     * @param reverse if true a detached edgeState with reversed properties is created where base
     *                and adjacent nodes, flags and wayGeometry are in reversed order. See #162 for more details
     *                about why we need the reverse parameter.
     */
    EdgeIteratorState detach(boolean reverse);

    /**
     * Copies the properties of the specified edge into this edge. Does not change nodes!
     *
     * @return the specified edge e
     */
    EdgeIteratorState copyPropertiesFrom(EdgeIteratorState e);
}
