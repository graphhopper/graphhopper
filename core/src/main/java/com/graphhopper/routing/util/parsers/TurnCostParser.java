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
package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.OSMTurnRelation;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.storage.Graph;

import java.util.List;

/**
 * This interface serves the purpose of converting relation flags into turn cost information. Unlike RelationTagParser
 * it can be assumed that the graph topology is already intact when handleTurnRelationTags is called.
 */
public interface TurnCostParser {
    String getName();

    void handleTurnRelationTags(OSMTurnRelation turnRelation, ExternalInternalMap map, Graph graph);

    /**
     * This map associates the internal GraphHopper nodes IDs with external IDs (OSM) and similarly for the edge IDs
     * required to write the turn costs. Returns -1 if there is no entry for the given OSM ID.
     */
    interface ExternalInternalMap {
        int getInternalNodeIdOfOsmNode(long nodeOsmId);

        long getOsmIdOfInternalEdge(int edgeId);
    }
}
