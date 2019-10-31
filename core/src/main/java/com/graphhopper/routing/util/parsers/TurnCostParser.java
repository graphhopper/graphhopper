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
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.IntsRef;

import java.util.Collection;
import java.util.List;

/**
 * This interface serves the purpose of converting relation flags into turn cost information. Unlike RelationTagParser
 * it can be assumed that the graph topology is already intact when create is called.
 */
public interface TurnCostParser {
    String getName();

    void createTurnCostEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue);

    Collection<TCEntry> handleTurnRelationTags(OSMTurnRelation turnRelation, IntsRef turnCostFlags,
                                               OSMInternalMap map, Graph graph);

    /**
     * Helper class to processing purposes. We could remove it if TurnCostExtension is similarly fast with merging
     * existing turn cost relations.
     */
    class TCEntry {
        public final int edgeFrom;
        public final int nodeVia;
        public final int edgeTo;
        public final IntsRef flags;

        public TCEntry(IntsRef flags, int edgeFrom, int nodeVia, int edgeTo) {
            this.edgeFrom = edgeFrom;
            this.nodeVia = nodeVia;
            this.edgeTo = edgeTo;
            this.flags = flags;
        }

        /**
         * @return an unique id (edgeFrom, edgeTo) to avoid duplicate entries if multiple encoders
         * are involved.
         */
        public long getItemId() {
            return ((long) edgeFrom) << 32 | ((long) edgeTo);
        }

        public void mergeFlags(TCEntry tce) {
            flags.ints[0] |= tce.flags.ints[0];
        }

        @Override
        public String toString() {
            return "*-(" + edgeFrom + ")->" + nodeVia + "-(" + edgeTo + ")->*";
        }
    }

    interface OSMInternalMap {
        int getInternalNodeIdOfOsmNode(long nodeOsmId);

        long getOsmIdOfInternalEdge(int edgeId);
    }
}
