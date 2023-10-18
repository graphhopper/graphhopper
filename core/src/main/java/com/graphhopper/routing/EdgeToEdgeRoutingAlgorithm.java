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
package com.graphhopper.routing;

import com.graphhopper.util.EdgeIterator;

public interface EdgeToEdgeRoutingAlgorithm extends RoutingAlgorithm {
    /**
     * like {@link #calcPath(int, int)}, but this method also allows to strictly restrict the edge the
     * path will begin with and the edge it will end with.
     *
     * @param fromOutEdge the edge id of the first edge of the path. using {@link EdgeIterator#ANY_EDGE} means
     *                    not enforcing the first edge of the path
     * @param toInEdge    the edge id of the last edge of the path. using {@link EdgeIterator#ANY_EDGE} means
     *                    not enforcing the last edge of the path
     */
    Path calcPath(int from, int to, int fromOutEdge, int toInEdge);
}
