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
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.Surface;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;

import java.util.List;

import static com.graphhopper.routing.profiles.Surface.*;

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
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, EncodingManager.Access access, long relationFlags) {
        String surfaceTag = readerWay.getTag("surface");
        Surface surface = Surface.find(surfaceTag);
        if (surface == OTHER && !Helper.isEmpty(surfaceTag)) {
            if (surfaceTag.equals("paving_stones") || surfaceTag.equals("metal") || surfaceTag.startsWith("concrete"))
                surface = PAVED;
            else if (surfaceTag.equals("sett"))
                surface = COBBLESTONE;
        }

        if (surface != OTHER)
            surfaceEnc.setEnum(false, edgeFlags, surface);
        return edgeFlags;
    }
}
