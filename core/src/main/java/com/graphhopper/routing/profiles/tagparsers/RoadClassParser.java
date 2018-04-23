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

import java.util.Arrays;
import java.util.List;


public class RoadClassParser implements TagParser {
    private final StringEncodedValue ev;

    List<String> roadClasses = Arrays.asList("_default", "footway", "path", "steps", "pedestrian", "living_street", "track",
            "residential", "service", "trunk", "trunk_link", "motorway", "motorway_link", "motorroad",
            "primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link",
            "cycleway", "unclassified", "road", "bridleway");

    public RoadClassParser(){
        this.ev = new StringEncodedValue(TagParserFactory.ROAD_CLASS, roadClasses, "_default");
    }
    public RoadClassParser(StringEncodedValue ev){this.ev = ev;}

    public void parse(IntsRef ints, ReaderWay way) {
        ev.setString(false, ints, way.getTag("highway"));
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
