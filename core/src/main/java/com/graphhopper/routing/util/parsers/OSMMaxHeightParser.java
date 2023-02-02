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
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.IntAccess;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.storage.IntsRef;

import java.util.Arrays;
import java.util.List;

public class OSMMaxHeightParser implements TagParser {

    private final DecimalEncodedValue heightEncoder;

    public OSMMaxHeightParser(DecimalEncodedValue heightEncoder) {
        this.heightEncoder = heightEncoder;
    }

    @Override
    public void handleWayTags(int edgeId, IntAccess intAccess, ReaderWay way, IntsRef relationFlags) {
        List<String> heightTags = Arrays.asList("maxheight", "maxheight:physical"/*, the OSM tag "height" is not used for the height of a road, so omit it here! */);
        OSMValueExtractor.extractMeter(edgeId, intAccess, way, heightEncoder, heightTags);
    }
}
