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
import com.graphhopper.routing.profiles.ObjectEncodedValue;
import com.graphhopper.routing.profiles.RoadAccess;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;

import java.util.Arrays;
import java.util.List;


public class OSMRoadAccessParser implements TagParser {
    private final ObjectEncodedValue roadAccessEnc;
    private final List<String> restrictions;

    public OSMRoadAccessParser() {
        this(RoadAccess.create());
    }

    public OSMRoadAccessParser(ObjectEncodedValue roadAccessEnc) {
        this(roadAccessEnc, Arrays.asList("motorcar", "motor_vehicle", "vehicle", "access"));
    }

    public OSMRoadAccessParser(ObjectEncodedValue roadAccessEnc, List<String> restrictions) {
        this.roadAccessEnc = roadAccessEnc;
        this.restrictions = restrictions;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> list) {
        list.add(roadAccessEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, EncodingManager.Access access, long relationFlags) {
        RoadAccess accessValue = RoadAccess.UNLIMITED;
        RoadAccess tmpAccessValue;
        for (String restriction : restrictions) {
            tmpAccessValue = RoadAccess.find(readerWay.getTag(restriction, "yes"));
            if (tmpAccessValue != null && tmpAccessValue.ordinal() > accessValue.ordinal()) {
                accessValue = tmpAccessValue;
            }
        }

        // TODO spatial rule
//        if (accessValue == RoadAccess.OTHER) {
//            // TODO Fix transportation mode when adding other forms of transportation
//            switch (getSpatialRule(way).getAccess(way.getTag("highway", ""), TransportationMode.MOTOR_VEHICLE, YES)) {
//                case YES:
//                    accessValue = RoadAccess.UNLIMITED;
//                    break;
//                case CONDITIONAL:
//                    accessValue = RoadAccess.DESTINATION;
//                    break;
//                case NO:
//                    accessValue = RoadAccess.NO;
//                    break;
//            }
//        }

        roadAccessEnc.setObject(false, edgeFlags, accessValue);
        return edgeFlags;
    }
}
