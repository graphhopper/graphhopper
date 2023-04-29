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
import com.graphhopper.reader.osm.OSMArea;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Landuse;
import com.graphhopper.storage.IntsRef;

import java.util.List;

import static com.graphhopper.routing.ev.Landuse.OTHER;

public class OSMLanduseParser implements TagParser {

    private final EnumEncodedValue<Landuse> landuseEnc;

    public OSMLanduseParser(EnumEncodedValue<Landuse> landuseEnc) {
        this.landuseEnc = landuseEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay readerWay, IntsRef relationFlags) {
        List<OSMArea> osmAreas = readerWay.getTag("gh:osm_areas", null);
        if (!osmAreas.isEmpty()) {
            // todonow: we simply use the smallest one for now, they are already sorted by size
            OSMArea osmArea = osmAreas.get(0);
            String landuseStr = (String) osmArea.getTags().get("landuse");
            Landuse landuse = Landuse.find(landuseStr);
            if (landuse != OTHER)
                landuseEnc.setEnum(false, edgeId, edgeIntAccess, landuse);
        }
    }
}
