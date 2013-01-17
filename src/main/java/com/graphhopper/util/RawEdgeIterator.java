/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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
 * Used as return value for Graph.getAllEdges. Different to EdgeIterator as we
 * don't have an access direction (where we could say 'base' versus 'adjacent'
 * node).
 *
 * @see com.graphhopper.storage.Graph
 * @author Peter Karich
 */
public interface RawEdgeIterator {

    boolean next();

    /**
     * @return node smaller or equal to B
     */
    int nodeA();

    /**
     * @return node greater than A
     */
    int nodeB();

    double distance();

    void distance(double dist);

    int flags();

    void flags(int flags);

    int edge();

    boolean isEmpty();
}
