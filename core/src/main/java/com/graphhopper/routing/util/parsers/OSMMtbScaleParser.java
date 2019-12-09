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

import static com.graphhopper.util.Helper.isEmpty;

import java.util.List;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.MtbScale;
import com.graphhopper.routing.profiles.UnsignedIntEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;

public class OSMMtbScaleParser implements TagParser {

    private final UnsignedIntEncodedValue scaleEncoder;

    public OSMMtbScaleParser() {
        this(MtbScale.create());
    }

    public OSMMtbScaleParser(UnsignedIntEncodedValue scaleEncoder) {
        this.scaleEncoder = scaleEncoder;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        registerNewEncodedValue.add(scaleEncoder);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, EncodingManager.Access access, IntsRef relationFlags) {
        String value = readerWay.getTag("mtb:scale");
        if (isEmpty(value)) {
            return edgeFlags;
        }
        try {
            int val = Integer.parseInt(value);
            if (val > scaleEncoder.getMaxInt()) {
                val = scaleEncoder.getMaxInt();
            }
            if (val < 0) {
                val = 0;
            }
            scaleEncoder.setInt(false, edgeFlags, val);
        } catch (NumberFormatException ex) {
        }
        return edgeFlags;
    }
}
