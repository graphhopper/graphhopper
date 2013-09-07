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
 * Iterates through all edges of one node. Avoids object creation in-between via direct access
 * methods.
 * <p/>
 * Usage:
 * <pre>
 * // calls to iter.adjNode(), distance() without next() will cause undefined behaviour
 * EdgeIterator iter = graph.getOutgoing(nodeId);
 * // or similar
 * EdgeIterator iter = graph.getIncoming(nodeId);
 * while(iter.next()) {
 *   int baseNodeId = iter.baseNode(); // equal to nodeId
 *   int adjacentNodeId = iter.adjNode();
 *   ...
 * }
 *
 * @author Peter Karich
 */
public interface EdgeIterator extends EdgeBase
{
    /**
     * To be called to go to the next edge state.
     */
    boolean next();
    
    EdgeBase detach();
    
    /**
     * integer value to indicate if an edge is valid or not which then would be initialized with
     * this value
     */
    public static final int NO_EDGE = -1;

    static class Edge
    {
        public static boolean isValid( int edgeId )
        {
            return edgeId > NO_EDGE;
        }
    }
}
