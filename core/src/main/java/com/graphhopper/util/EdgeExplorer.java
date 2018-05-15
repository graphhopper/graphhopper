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

/**
 * Class to get an EdgeIterator. Create it via graph.createEdgeExplorer() use one instance per
 * thread.
 * <p>
 *
 * @author Peter Karich
 * @see EdgeIterator
 * @see EdgeIteratorState
 */
public interface EdgeExplorer {
    /**
     * This method sets the base node for iteration through neighboring edges (EdgeIteratorStates).
     *
     * @return EdgeIterator around the specified baseNode. The resulting iterator can be a new
     * instance or a reused instance returned in a previous call. So be sure you do not use the
     * EdgeExplorer from multiple threads or in a nested loop.
     */
    EdgeIterator setBaseNode(int baseNode);
}
