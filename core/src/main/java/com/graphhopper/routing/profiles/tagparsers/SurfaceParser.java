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

import static com.graphhopper.routing.profiles.TagParserFactory.ACCEPT_IF_HIGHWAY;


public class SurfaceParser implements TagParser {
    private StringEncodedValue ev;

    static List<String> surfaces = Arrays.asList("_default", "paved", "asphalt", "cobblestone", "cobblestone:flattened", "sett", "concrete",
            "concrete:lanes", "concrete:plates", "paving_stones", "paving_stones:30", "unpaved", "compacted"
            , "dirt", "earth", "fine_gravel", "grass", "grass_paver", "gravel", "ground", "ice", "metal"
            , "mud", "pebblestone", "salt", "sand", "wood");

    public SurfaceParser(){
        this.ev = new StringEncodedValue(TagParserFactory.SURFACE, surfaces, "_default");
    }

    public void parse(IntsRef ints, ReaderWay way) {
        ev.setString(false, ints, way.getTag("surface"));
    }

    public ReaderWayFilter getReadWayFilter() {
        return ACCEPT_IF_HIGHWAY;
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
