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
package com.graphhopper.routing.profiles.tagparsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRuleLookup;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.shapes.GHPoint;


public class SpatialRuleIdParser implements TagParser {
    private IntEncodedValue ev;
    public SpatialRuleIdParser(){
        //TODO NOW Use correct SpatialRuleLookup
//        int tmpMax = spatialRuleLookup.size() - 1;
//        int bits = 32 - Integer.numberOfLeadingZeros(tmpMax);
//        if (bits > 0)
//            this.ev = new IntEncodedValue(TagParserFactory.SPATIAL_RULE_ID, bits, 0, false);
    }

    public void parse(IntsRef ints, ReaderWay way) {
        GHPoint estimatedCenter = way.getTag("estimated_center", null);
//        if (estimatedCenter != null) {
//            SpatialRule rule = spatialRuleLookup.lookupRule(estimatedCenter);
//            spatialId.setInt(false, ints, spatialRuleLookup.getSpatialId(rule));
//        }
    }


    public ReaderWayFilter getReadWayFilter() {
        return TagParserFactory.ACCEPT_IF_HIGHWAY;
    }

    public final EncodedValue getEncodedValue() {
        return ev;
    }

    public final String toString() {
        return ev.toString();
    }

    public final String getName() {
        return ev.getName();
    }


}
