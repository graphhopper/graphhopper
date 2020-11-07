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
 * Parses the horseback riding difficulty. Where common is mapped to R1, demanding to R2 etc and R7 is unused.
 *
 * @see <a href="https://wiki.openstreetmap.org/wiki/Key:horse_scale">Key:horse_scale</a> for details on horseback riding difficulties.
 */
public class OSMHorseRatingParser implements TagParser {

    private final EnumEncodedValue<Rating> horseScale;

    public OSMHorseRatingParser() {
        this.horseScale = HorseRating.create();
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> link) {
        link.add(horseScale);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, boolean ferry, IntsRef relationFlags) {
        String scale = readerWay.getTag("horse_scale");
        Rating rating = Rating.MISSING;
        if (scale != null) {
            if (scale.equals("common")) rating = Rating.R1;
            else if (scale.equals("demanding")) rating = Rating.R2;
            else if (scale.equals("difficult")) rating = Rating.R3;
            else if (scale.equals("critical")) rating = Rating.R4;
            else if (scale.equals("dangerous")) rating = Rating.R5;
            else if (scale.equals("impossible")) rating = Rating.R6;
        }
        if (rating != Rating.MISSING)
            horseScale.setEnum(false, edgeFlags, rating);
        return edgeFlags;
    }
}
