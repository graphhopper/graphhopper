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

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.routing.util.spatialrules.SpatialRuleSet;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.shapes.GHPoint;

import java.util.List;

/**
 * This parser stores the spatialId in the edgeFlags based on previously defined areas.
 */
public class SpatialRuleParser implements TagParser {

    private final IntEncodedValue spatialRuleEnc;
    private SpatialRuleLookup spatialRuleLookup;

    public SpatialRuleParser(SpatialRuleLookup spatialRuleLookup, IntEncodedValue spatialRuleEnc) {
        this.spatialRuleLookup = spatialRuleLookup;
        if (spatialRuleEnc == null)
            throw new IllegalStateException("SpatialRuleLookup was not initialized before building the EncodingManager");
        this.spatialRuleEnc = spatialRuleEnc;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        registerNewEncodedValue.add(spatialRuleEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, boolean ferry, IntsRef relationFlags) {
        GHPoint estimatedCenter = way.getTag("estimated_center", null);
        if (estimatedCenter != null) {
            SpatialRuleSet ruleSet = spatialRuleLookup.lookupRules(estimatedCenter.lat, estimatedCenter.lon);
            way.setTag("spatial_rule_set", ruleSet);
            spatialRuleEnc.setInt(false, edgeFlags, ruleSet.getSpatialId());
        }
        return edgeFlags;
    }
}
