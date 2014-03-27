/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
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

/**
 * This interface represents an edge and is one possible state of an EdgeIterator.
 * <p/>
 * @see EdgeIterator
 * @see EdgeExplorer
 * @author Peter Karich
 */
public interface EdgeIteratorState
{
    /**
     * @return the edge id of the current edge. Do not make any assumptions about the concrete
     * values, except that for an implemention it is recommended that they'll be contiguous.
     */
    int getEdge();

    /**
     * Returns the node used to instantiate the EdgeIterator. Example: "EdgeIterator iter =
     * graph.getEdges(baseNode)". Often only used for convenience reasons. Do not confuse this with
     * a <i>source node</i> of a directed edge.
     * <p/>
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
     * For OSM a way is often a curve not just a straight line. These nodes are called pillar nodes
     * and are between tower nodes (which are used for routing), they are necessary to have a more
     * exact geometry. Updates to the returned list are not reflected in the graph, for that you've
     * to use setWayGeometry.
     * <p/>
     * @param mode can be <ul> <li>0 = only pillar nodes, no tower nodes</li> <li>1 = inclusive the
     * base tower node only</li> <li>2 = inclusive the adjacent tower node only</li> <li>3 =
     * inclusive the base and adjacent tower node</li> </ul>
     * @return pillar nodes
     */
    PointList fetchWayGeometry( int mode );

    /**
     * @param list is a sorted collection of nodes between the baseNode and the current adjacent
     * node. Specify the list without the adjacent and base nodes.
     */
    EdgeIteratorState setWayGeometry( PointList list );

    /**
     * @return the distance of the current edge in meter
     */
    double getDistance();

    EdgeIteratorState setDistance( double dist );

    long getFlags();

    EdgeIteratorState setFlags( long flags );

    /**
     * @return the additional field value for this edge
     */
    int getAdditionalField();

    /**
     * Updates the additional field value for this edge
     */
    EdgeIteratorState setAdditionalField( int value );

    String getName();

    EdgeIteratorState setName( String name );

    /**
     * Clones this EdgeIteratorState.
     * <p>
     * @param reverse if true a detached edgeState with reversed properties is created where base
     * and adjacent nodes, flags and wayGeometry are in reversed order. See #162 for more details
     * about why we need the new reverse parameter.
     */
    EdgeIteratorState detach( boolean reverse );

    /**
     * Copies the properties of this edge into the specified edge. Does not change nodes!
     * <p>
     * @return the specified edge e
     */
    EdgeIteratorState copyPropertiesTo( EdgeIteratorState e );
}
