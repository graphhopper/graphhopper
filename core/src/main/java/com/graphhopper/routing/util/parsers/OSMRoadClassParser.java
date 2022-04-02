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
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.storage.IntsRef;

import java.util.List;

import static com.graphhopper.routing.ev.RoadClass.OTHER;

public class OSMRoadClassParser implements TagParser {

    private final EnumEncodedValue<RoadClass> roadClassEnc;

    public OSMRoadClassParser() {
        this(new EnumEncodedValue<>(RoadClass.KEY, RoadClass.class));
    }

    public OSMRoadClassParser(EnumEncodedValue<RoadClass> roadClassEnc) {
        this.roadClassEnc = roadClassEnc;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> link) {
        link.add(roadClassEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, IntsRef relationFlags) {
        String roadClassTag = readerWay.getTag("highway");
        if (roadClassTag == null)
            return edgeFlags;
        RoadClass roadClass = RoadClass.find(roadClassTag);
        if (roadClass == OTHER && roadClassTag.endsWith("_link"))
            roadClass = RoadClass.find(roadClassTag.substring(0, roadClassTag.length() - 5));

        if (roadClass != OTHER)
            roadClassEnc.setEnum(false, edgeFlags, roadClass);
        return edgeFlags;
    }
}
