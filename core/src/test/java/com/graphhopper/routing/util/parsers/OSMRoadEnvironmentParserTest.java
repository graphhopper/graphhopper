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
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.BytesRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OSMRoadEnvironmentParserTest {

    @Test
    void ferry() {
        EnumEncodedValue<RoadEnvironment> roadEnvironmentEnc = RoadEnvironment.create();
        roadEnvironmentEnc.init(new EncodedValue.InitializerConfig());
        OSMRoadEnvironmentParser parser = new OSMRoadEnvironmentParser(roadEnvironmentEnc);
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        ReaderWay way = new ReaderWay(0);
        way.setTag("route", "shuttle_train");
        parser.handleWayTags(edgeId, edgeAccess, way, new BytesRef(8));
        RoadEnvironment roadEnvironment = roadEnvironmentEnc.getEnum(false, edgeId, edgeAccess);
        assertEquals(RoadEnvironment.FERRY, roadEnvironment);

        way = new ReaderWay(1);
        way.setTag("highway", "footway");
        way.setTag("route", "ferry");
        parser.handleWayTags(edgeId, edgeAccess = new EdgeBytesAccessArray(4), way, new BytesRef(8));
        roadEnvironment = roadEnvironmentEnc.getEnum(false, edgeId, edgeAccess);
        assertEquals(RoadEnvironment.FERRY, roadEnvironment);
    }

}
