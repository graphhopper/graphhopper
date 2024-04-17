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
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.HighwayFeature;
import com.graphhopper.storage.IntsRef;
import java.util.List;
import java.util.Map;

/**
 * Can be used to parse out Highway features from OSM tag data. See
 * <a href="https://wiki.openstreetmap.org/wiki/Key:highway">osm highway tag</a>
 * - 'Other highway features' section for potentially more values to add here in the future.
 */
public class OSMHighwayFeatureParser implements TagParser {

    protected final EnumEncodedValue<HighwayFeature> highwayFeatureEnc;

    public OSMHighwayFeatureParser(EnumEncodedValue<HighwayFeature> highwayFeatureEnc) {
        this.highwayFeatureEnc = highwayFeatureEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay readerWay, IntsRef relationFlags) {
        List<Map<String, Object>> nodeTags = readerWay.getTag("node_tags", null);
        if (nodeTags == null)
            return;

        for (int i = 0; i < nodeTags.size(); i++) {
            Map<String, Object> tags = nodeTags.get(i);
            String crossingValue = (String) tags.get("highway");
            HighwayFeature highwayFeature = HighwayFeature.find(crossingValue);
            if (highwayFeature != HighwayFeature.MISSING) {
                highwayFeatureEnc.setEnum(false, edgeId, edgeIntAccess, highwayFeature);
            }
        }
    }
}
