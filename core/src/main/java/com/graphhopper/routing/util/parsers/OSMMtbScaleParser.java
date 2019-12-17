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

import static com.graphhopper.routing.profiles.MtbScale.NONE;

import java.util.List;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.MtbScale;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;

public class OSMMtbScaleParser implements TagParser {

    private final EnumEncodedValue<MtbScale> scaleEncoder;

    public OSMMtbScaleParser() {
        this.scaleEncoder = new EnumEncodedValue<>(MtbScale.KEY, MtbScale.class);
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> link) {
        link.add(scaleEncoder);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, EncodingManager.Access access, IntsRef relationFlags) {
        if (!access.isWay())
            return edgeFlags;

        String value = readerWay.getTag("mtb:scale");
        if (value == null)
            return edgeFlags;

        // Drop '+' and '-' suffix if there is any. We do not store that level of detail because
        // there are not that many occurences and adding this level of detail would cost us
        // additional two bits per edge.
        MtbScale scale = NONE;
        if (value.length() == 1) {
            scale = MtbScale.find(value);
        } else if (value.length() == 2 && (value.charAt(1) == '+' || value.charAt(1) == '-')) {
            scale = MtbScale.find(value.substring(0, 1));
        }
        if (scale != NONE)
            scaleEncoder.setEnum(false, edgeFlags, scale);
        return edgeFlags;
    }
}
