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
import com.graphhopper.routing.ev.Surface;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;

import java.util.List;

import static com.graphhopper.routing.ev.Surface.*;

public class OSMSurfaceParser implements TagParser {

    private final EnumEncodedValue<Surface> surfaceEnc;

    public OSMSurfaceParser() {
        this(new EnumEncodedValue<>(KEY, Surface.class));
    }

    public OSMSurfaceParser(EnumEncodedValue<Surface> surfaceEnc) {
        this.surfaceEnc = surfaceEnc;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> list) {
        list.add(surfaceEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, boolean ferry, IntsRef relationFlags) {
        String surfaceTag = readerWay.getTag("surface");
        Surface surface = Surface.find(surfaceTag);
        if (surface == OTHER && !Helper.isEmpty(surfaceTag)) {
            if (surfaceTag.equals("metal"))
                surface = PAVED;
            else if (surfaceTag.equals("sett"))
                surface = COBBLESTONE;
            else if (surfaceTag.equals("wood"))
                surface = UNPAVED;
            else if (surfaceTag.equals("earth"))
                surface = DIRT;
        }

        if (surface != OTHER)
            surfaceEnc.setEnum(false, edgeFlags, surface);
        return edgeFlags;
    }
}
