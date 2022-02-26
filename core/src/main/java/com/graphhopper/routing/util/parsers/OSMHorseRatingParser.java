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
import com.graphhopper.routing.ev.HorseRating;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.storage.IntsRef;

import java.util.List;

/**
 * Parses the horseback riding difficulty. Where common is mapped to 1, demanding to 2 until 6
 *
 * @see <a href="https://wiki.openstreetmap.org/wiki/Key:horse_scale">Key:horse_scale</a> for details on horseback riding difficulties.
 */
public class OSMHorseRatingParser implements TagParser {

    private final IntEncodedValue horseScale;

    public OSMHorseRatingParser() {
        this.horseScale = HorseRating.create();
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> link) {
        link.add(horseScale);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, IntsRef relationFlags) {
        String scale = readerWay.getTag("horse_scale");
        int rating = 0;
        if (scale != null) {
            if (scale.equals("common")) rating = 1;
            else if (scale.equals("demanding")) rating = 2;
            else if (scale.equals("difficult")) rating = 3;
            else if (scale.equals("critical")) rating = 4;
            else if (scale.equals("dangerous")) rating = 5;
            else if (scale.equals("impossible")) rating = 6;
        }
        if (rating != 0)
            horseScale.setInt(false, edgeFlags, rating);
        return edgeFlags;
    }
}
