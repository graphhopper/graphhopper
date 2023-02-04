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
package com.graphhopper.storage;

import com.graphhopper.core.util.PointAccess;

/**
 * This interface specifies how to access properties of the nodes in the graph. Similar to
 * EdgeExplorer as it needs multiple instances for different threads or loops but without the need
 * for an additional iterator.
 * <p>
 *
 * @author Peter Karich
 */
public interface NodeAccess extends PointAccess {
    /**
     * @return the index used to retrieve turn cost information for this node, can be {@link TurnCostStorage#NO_TURN_ENTRY}
     *         in case no turn costs were stored for this node
     * @throws AssertionError if, and only if, the underlying storage does not support turn costs
     */
    int getTurnCostIndex(int nodeId);

    /**
     * Sets the turn cost index for this node, using {@link TurnCostStorage#NO_TURN_ENTRY} means there
     * are no turn costs at this node.
     * <p>
     *
     * @throws AssertionError if, and only if, the underlying storage does not support turn costs
     */
    void setTurnCostIndex(int nodeId, int additionalValue);
}
