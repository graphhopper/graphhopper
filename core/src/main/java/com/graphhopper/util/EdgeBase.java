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
 * @author Peter Karich
 */
public interface EdgeBase {

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
     * For OSM a way is often a curve not just a straight line and nodes between tower nodes are
     * necessary to have a more exact geometry. Those nodes are called pillar nodes and will be
     * returned in this method.
     * <p/>
     * @return pillar nodes
     */
    PointList getWayGeometry();

    /**
     * @param list is a sorted collection of nodes between the baseNode and the current adjacent
     * node
     */
    void setWayGeometry( PointList list );

    /**
     * @return the distance of the current edge edge
     */
    double getDistance();

    void setDistance( double dist );

    int getFlags();

    void setFlags( int flags );

    String getName();

    void setName( String name );
}
