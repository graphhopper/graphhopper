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

package com.graphhopper.tardur;

import com.graphhopper.reader.OSMTurnRestriction;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.TurnCost;
import com.graphhopper.routing.profiles.UnsignedIntEncodedValue;
import com.graphhopper.routing.util.parsers.TurnRestrictionParser;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

class ConditionalTurnRestrictionParser implements TurnRestrictionParser {
    private UnsignedIntEncodedValue tagPointer;

    @Override
    public String getName() {
        return "ulrich";
    }

    @Override
    public void createTurnCostEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        tagPointer = new UnsignedIntEncodedValue("turnrestrictiontagpointer", 31, false);
        registerNewEncodedValue.add(tagPointer);
    }

    @Override
    public void handleTurnRestriction(IntsRef turnCostFlags, OSMTurnRestriction turnRestriction, ExternalInternalMap map, Graph graph) {
        TurnCostStorage tcs = graph.getTurnCostStorage();
        List<TimeDependentRestrictionsDAO.ConditionalTagData> restrictionData =
                TimeDependentRestrictionsDAO
                        .getConditionalTagDataWithTimeDependentConditions(TimeDependentRestrictionsDAO.sanitize(turnRestriction.getReaderRelation().getTags()))
                        .stream()
                        .filter(c -> !c.restrictionData.isEmpty())
                        .collect(Collectors.toList());

        int viaNode = map.getInternalNodeIdOfOsmNode(turnRestriction.getViaOsmNodeId());
        if (!restrictionData.isEmpty()) {
            System.out.println("pups");

            int edgeIdFrom = EdgeIterator.NO_EDGE;

            // get all incoming edges and receive the edge which is defined by fromOsm
            EdgeExplorer edgeInExplorer = graph.createEdgeExplorer();
            EdgeIterator iter = edgeInExplorer.setBaseNode(viaNode);

            while (iter.next()) {
                if (map.getOsmIdOfEdge(iter.getEdge()) == turnRestriction.getOsmIdFrom()) {
                    edgeIdFrom = iter.getEdge();
                    break;
                }
            }

            if (!EdgeIterator.Edge.isValid(edgeIdFrom))
                return;

            HashMap<String, String> tagss = new HashMap<>();
            for (TimeDependentRestrictionsDAO.ConditionalTagData timeDependentAccessCondition : restrictionData) {
                tagss.put(timeDependentAccessCondition.tag.key, timeDependentAccessCondition.tag.value);
            }
            int offset = (int) ((GraphHopperStorage) graph).getTagStore().add(tagss);


            // get all outgoing edges of the via node
            iter = edgeInExplorer.setBaseNode(viaNode);
            // for TYPE_ONLY_* we add ALL restrictions (from, via, * ) EXCEPT the given turn
            // for TYPE_NOT_*  we add ONE restriction  (from, via, to)
            while (iter.next()) {
                int edgeIdTo = iter.getEdge();
                IntsRef intsRef = tcs.readFlags(TurnCost.createFlags(), edgeIdFrom, viaNode, edgeIdTo);
                tagPointer.setInt(false, intsRef, offset);
                int edgeId = iter.getEdge();
                long wayId = map.getOsmIdOfEdge(edgeId);
                OSMTurnRestriction.Type restrictionType = turnRestriction.getRestrictionType();
                if (edgeId != edgeIdFrom && restrictionType == OSMTurnRestriction.Type.ONLY && wayId != turnRestriction.getOsmIdTo()
                        || restrictionType == OSMTurnRestriction.Type.NOT && wayId == turnRestriction.getOsmIdTo() && wayId >= 0) {
                    tcs.setTurnCost(intsRef, edgeIdFrom, viaNode, edgeIdTo);
                    if (restrictionType == OSMTurnRestriction.Type.NOT)
                        break;
                }
            }
        }
    }
}
