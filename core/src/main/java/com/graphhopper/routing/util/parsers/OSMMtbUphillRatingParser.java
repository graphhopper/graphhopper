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
import com.graphhopper.routing.ev.MtbUphillRating;
import com.graphhopper.routing.ev.UnsignedIntEncodedValue;
import com.graphhopper.storage.IntsRef;

import java.util.List;

public class OSMMtbUphillRatingParser implements TagParser {

    private final UnsignedIntEncodedValue ratingEncoder;

    public OSMMtbUphillRatingParser() {
        this.ratingEncoder = new UnsignedIntEncodedValue(MtbUphillRating.KEY, 3, true);
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> link) {
        link.add(ratingEncoder);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, boolean ferry, IntsRef relationFlags) {
        String value = readerWay.getTag("mtb:scale:uphill");
        MtbUphillRating rating = MtbUphillRating.find(value);
        if (rating != MtbUphillRating.NONE) {
            ratingEncoder.setInt(false, edgeFlags, rating.ordinal());
        }
        return edgeFlags;
    }
}
