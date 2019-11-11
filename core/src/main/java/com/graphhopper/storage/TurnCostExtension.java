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

public interface TurnCostExtension extends Storable<TurnCostExtension> {
    int NO_TURN_ENTRY = -1;

    void setSegmentSize(int bytes);

    void addTurnInfo(int fromEdge, int viaNode, int toEdge, long turnFlags);

    long getTurnCostFlags(int edgeFrom, int nodeVia, int edgeTo);

    boolean isUTurn(int edgeFrom, int edgeTo);

    boolean isUTurnAllowed(int node);

    TurnCostExtension copyTo(TurnCostExtension turnCostExtension);

    @Override
    boolean isClosed();

}
