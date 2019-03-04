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
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.shapes.GHPoint;

import java.util.List;

public class SpatialRuleParser implements TagParser {

    private final IntEncodedValue spatialIdEnc;
    private SpatialRuleLookup spatialRuleLookup;

    public SpatialRuleParser(SpatialRuleLookup spatialRuleLookup) {
        this(Country.KEY, spatialRuleLookup);
    }

    public SpatialRuleParser(String name, SpatialRuleLookup spatialRuleLookup) {
        this.spatialRuleLookup = spatialRuleLookup;
        int tmpMax = spatialRuleLookup.size() - 1;
        int bits = 32 - Integer.numberOfLeadingZeros(tmpMax);
        if (bits <= 0)
            throw new IllegalArgumentException("No bits left to store spatial ID");
        spatialIdEnc = new SimpleIntEncodedValue(name, bits, false);
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        if (spatialIdEnc == null)
            throw new IllegalStateException("SpatialRuleLookup was not initialized before building the EncodingManager");
        registerNewEncodedValue.add(spatialIdEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access, long relationFlags) {
        GHPoint estimatedCenter = way.getTag("estimated_center", null);
        if (estimatedCenter != null) {
            SpatialRule rule = spatialRuleLookup.lookupRule(estimatedCenter);
            way.setTag("spatial_rule", rule);
            spatialIdEnc.setInt(false, edgeFlags, spatialRuleLookup.getSpatialId(rule));
        }
        return edgeFlags;
    }
}
