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
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;

import java.util.Arrays;
import java.util.List;

public class MaxHeightParser implements TagParser {
    private DecimalEncodedValue ev;

    final List<String> heightTags = Arrays.asList("maxheight", "maxheight:physical");

    //TODO: Add correct init values
    public MaxHeightParser(){
        this.ev = new DecimalEncodedValue(TagParserFactory.MAX_HEIGHT, 7, 10, 2, false);
    }

    public void parse(IntsRef ints, ReaderWay way) {
        String value = way.getFirstPriorityTag(heightTags);
        if (Helper.isEmpty(value)) return;

        try {
            ev.setDecimal(false, ints, TagParserFactory.stringToMeter(value));
        } catch (Throwable ex) {
            TagParserFactory.LOGGER.warn("Unable to extract height from malformed road attribute '{}' for way (OSM_ID = {}).", value, way.getId(), ex);
            return;
        }
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
