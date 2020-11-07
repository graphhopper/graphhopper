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
import com.graphhopper.storage.IntsRef;

import java.util.List;

/**
 * Parses the hiking difficulty. Where hiking corresponds to R1, mountain_hiking to R2 etc and R7 is unused.
 *
 * @see <a href="https://wiki.openstreetmap.org/wiki/Key:sac_scale">Key:sac_scale</a> for details on OSM hiking difficulties.
 */
public class OSMHikeRatingParser implements TagParser {

    private final EnumEncodedValue<Rating> sacScaleEnc;

    public OSMHikeRatingParser() {
        this.sacScaleEnc = HikeRating.create();
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> link) {
        link.add(sacScaleEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, boolean ferry, IntsRef relationFlags) {
        String scale = readerWay.getTag("sac_scale");
        Rating rating = Rating.MISSING;
        if (scale != null) {
            if (scale.equals("hiking")) rating = Rating.R1;
            else if (scale.equals("mountain_hiking")) rating = Rating.R2;
            else if (scale.equals("demanding_mountain_hiking")) rating = Rating.R3;
            else if (scale.equals("alpine_hiking")) rating = Rating.R4;
            else if (scale.equals("demanding_alpine_hiking")) rating = Rating.R5;
            else if (scale.equals("difficult_alpine_hiking")) rating = Rating.R6;
        }
        if (rating != Rating.MISSING)
            sacScaleEnc.setEnum(false, edgeFlags, rating);

        return edgeFlags;
    }
}
