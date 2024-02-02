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

package com.graphhopper.reader.osm;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

public class PrepareDeadEnds {
    private final BaseGraph baseGraph;

    public PrepareDeadEnds(BaseGraph baseGraph) {
        this.baseGraph = baseGraph;
    }

    public void findDeadEndUTurns(Weighting weighting, BooleanEncodedValue deadEndEnc, BooleanEncodedValue subnetworkEnc) {
        EdgeExplorer inExplorer = baseGraph.createEdgeExplorer();
        EdgeExplorer outExplorer = baseGraph.createEdgeExplorer();
        for (int node = 0; node < baseGraph.getNodes(); node++) {
            EdgeIterator fromEdge = inExplorer.setBaseNode(node);
            OUTER:
            while (fromEdge.next()) {
                if (Double.isFinite(weighting.calcEdgeWeight(fromEdge, true))) {
                    boolean subnetworkFrom = fromEdge.get(subnetworkEnc);
                    EdgeIterator toEdge = outExplorer.setBaseNode(node);
                    while (toEdge.next()) {
                        if (toEdge.getEdge() != fromEdge.getEdge()
                                && Double.isFinite(GHUtility.calcWeightWithTurnWeight(weighting, toEdge, false, fromEdge.getEdge()))
                                && subnetworkFrom == toEdge.get(subnetworkEnc))
                            continue OUTER;
                    }
                    // the only way to continue from fromEdge is a u-turn. this is a dead-end u-turn
                    setDeadEndUTurn(baseGraph, deadEndEnc, fromEdge.getEdge(), node);
                }
            }
        }
    }

    private void setDeadEndUTurn(BaseGraph baseGraph, BooleanEncodedValue deadEndEnc, int fromEdge, int viaNode) {
        baseGraph.getTurnCostStorage().set(deadEndEnc, fromEdge, viaNode, fromEdge, true);
    }
}
