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
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.RoadAccess;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.TransportationMode;
import com.graphhopper.storage.IntsRef;

import java.util.Arrays;
import java.util.List;

import static com.graphhopper.routing.profiles.RoadAccess.YES;

public class OSMRoadAccessParser implements TagParser {
    private final EnumEncodedValue<RoadAccess> roadAccessEnc;
    private final List<String> restrictions;

    public OSMRoadAccessParser() {
        this(Arrays.asList("motorcar", "motor_vehicle", "vehicle", "access"));
    }

    public OSMRoadAccessParser(List<String> restrictions) {
        this.roadAccessEnc = new EnumEncodedValue<>(RoadAccess.KEY, RoadAccess.class);
        this.restrictions = restrictions;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> list) {
        list.add(roadAccessEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, EncodingManager.Access access, long relationFlags) {
        RoadAccess accessValue = YES;
        RoadAccess tmpAccessValue;
        for (String restriction : restrictions) {
            tmpAccessValue = RoadAccess.find(readerWay.getTag(restriction, "yes"));
            if (tmpAccessValue != null && tmpAccessValue.ordinal() > accessValue.ordinal()) {
                accessValue = tmpAccessValue;
            }
        }

        if (accessValue == RoadAccess.YES) {
            SpatialRule spatialRule = readerWay.getTag("spatial_rule", null);
            if (spatialRule != null)
                accessValue = spatialRule.getAccess(readerWay.getTag("highway", ""), TransportationMode.MOTOR_VEHICLE, YES);
        }

        roadAccessEnc.setEnum(false, edgeFlags, accessValue);
        return edgeFlags;
    }
}
