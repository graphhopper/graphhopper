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
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;

import java.util.List;

/**
 * This interface serves the purpose of converting relation flags into turn cost information. Unlike RelationTagParser
 * it can be assumed that the graph topology is already intact when create is called.
 */
public interface TurnCostParser {
    String getName();

    void createTurnCostEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue);

    // TODO NOW move OSMReader methods into implementation and see what we need here
    void create(Graph graph);

    /**
     * @return whether or not this parser should consider the given turn restriction
     * @see OSMTurnRelation
     */
    boolean acceptsTurnRelation(OSMTurnRelation relation);

    // TODO NOW remove the following 3 methods from the interface:
    DecimalEncodedValue getTurnCostEnc();

    EdgeExplorer createEdgeOutExplorer(Graph graph);

    EdgeExplorer createEdgeInExplorer(Graph graph);
}
