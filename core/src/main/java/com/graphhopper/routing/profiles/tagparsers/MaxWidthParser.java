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
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.ReaderWayFilter;
import com.graphhopper.routing.profiles.TagParserFactory;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;

import java.util.Arrays;
import java.util.List;


public class MaxWidthParser implements TagParser {
    private DecimalEncodedValue ev;

    final List<String> widthTags = Arrays.asList("maxwidth", "maxwidth:physical");


    //TODO: Add correct init values
    public MaxWidthParser(){
        this.ev = new DecimalEncodedValue(TagParserFactory.MAX_WIDTH, 6, 0, 0.1, false);
    }

    public void parse(IntsRef ints, ReaderWay way) {
        String value = way.getFirstPriorityTag(widthTags);
        if (Helper.isEmpty(value)) return;

        try {
            ev.setDecimal(false, ints, TagParserFactory.stringToMeter(value));
        } catch (Throwable ex) {
            TagParserFactory.LOGGER.warn("Unable to extract width from malformed road attribute '{}' for way (OSM_ID = {}).", value, way.getId(), ex);
            return;
        }
    }

    public ReaderWayFilter getReadWayFilter() {
        return TagParserFactory.SPEEDMAPFILTER;
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
