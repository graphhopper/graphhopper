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

import com.graphhopper.routing.profiles.*;
import com.graphhopper.storage.IntsRef;

/**
 * This interface represents an edge and is one possible state of an EdgeIterator.
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
        public boolean getBool(boolean reverse, IntsRef ref) {
            return reverse;
        }

        @Override
        public void setBool(boolean reverse, IntsRef ref, boolean value) {
            throw new IllegalStateException("reverse state cannot be modified");
        }
    };

    /**
     * @return the edge id of the current edge. Do not make any assumptions about the concrete
     * values, except that for an implementation it is recommended that they'll be contiguous.
     */
    int getEdge();

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
     * Returns the node used to instantiate the EdgeIterator. Example: "EdgeIterator iter =
     * graph.getEdges(baseNode)". Often only used for convenience reasons. Do not confuse this with
     * a <i>source node</i> of a directed edge.
     * <p>
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
     * For road network data like OSM a way is nearly always a curve not just a straight line. These
     * nodes are called pillar nodes and are between tower nodes (which are used for routing), they
     * are necessary to have a more exact geometry. See the docs for more information
     * (docs/core/low-level-api.md#what-are-pillar-and-tower-nodes). Updates to the returned list
     * are not reflected in the graph, for that you've to use setWayGeometry.
     * <p>
     *
     * @param mode can be <ul> <li>0 = only pillar nodes, no tower nodes</li> <li>1 = inclusive the
     *             base tower node only</li> <li>2 = inclusive the adjacent tower node only</li> <li>3 =
     *             inclusive the base and adjacent tower node</li> </ul>
     * @return pillar nodes
     */
    PointList fetchWayGeometry(int mode);

    /**
     * @param list is a sorted collection of nodes between the baseNode and the current adjacent
     *             node. Specify the list without the adjacent and base nodes.
     */
    EdgeIteratorState setWayGeometry(PointList list);

    /**
     * @return the distance of the current edge in meter
     */
    double getDistance();

    EdgeIteratorState setDistance(double dist);

    /**
     * Returns edge properties stored in direction of the raw database layout. So do not use it directly, instead
     * use the appropriate set/get methods with its EncodedValue object.
     */
    IntsRef getFlags();

    /**
     * Stores the specified edgeFlags down to the DataAccess
     */
    EdgeIteratorState setFlags(IntsRef edgeFlags);

    /**
     * @return the additional field value for this edge
     */
    int getAdditionalField();

    /**
     * Updates the additional field value for this edge
     */
    EdgeIteratorState setAdditionalField(int value);

    boolean get(BooleanEncodedValue property);

    EdgeIteratorState set(BooleanEncodedValue property, boolean value);

    boolean getReverse(BooleanEncodedValue property);

    EdgeIteratorState setReverse(BooleanEncodedValue property, boolean value);

    int get(IntEncodedValue property);

    EdgeIteratorState set(IntEncodedValue property, int value);

    int getReverse(IntEncodedValue property);

    EdgeIteratorState setReverse(IntEncodedValue property, int value);

    double get(DecimalEncodedValue property);

    EdgeIteratorState set(DecimalEncodedValue property, double value);

    double getReverse(DecimalEncodedValue property);

    EdgeIteratorState setReverse(DecimalEncodedValue property, double value);

    <T extends Enum> T get(EnumEncodedValue<T> property);

    <T extends Enum> EdgeIteratorState set(EnumEncodedValue<T> property, T value);

    <T extends Enum> T getReverse(EnumEncodedValue<T> property);

    <T extends Enum> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value);

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
