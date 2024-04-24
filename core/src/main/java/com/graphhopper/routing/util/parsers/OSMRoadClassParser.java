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
import com.graphhopper.routing.ev.EdgeBytesAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.storage.BytesRef;

import static com.graphhopper.routing.ev.RoadClass.OTHER;

public class OSMRoadClassParser implements TagParser {

    protected final EnumEncodedValue<RoadClass> roadClassEnc;

    public OSMRoadClassParser(EnumEncodedValue<RoadClass> roadClassEnc) {
        this.roadClassEnc = roadClassEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeBytesAccess edgeAccess, ReaderWay readerWay, BytesRef relationFlags) {
        String roadClassTag = readerWay.getTag("highway");
        if (roadClassTag == null)
            return;
        RoadClass roadClass = RoadClass.find(roadClassTag);
        if (roadClass == OTHER && roadClassTag.endsWith("_link"))
            roadClass = RoadClass.find(roadClassTag.substring(0, roadClassTag.length() - 5));

        if (roadClass != OTHER)
            roadClassEnc.setEnum(false, edgeId, edgeAccess, roadClass);
    }
}
