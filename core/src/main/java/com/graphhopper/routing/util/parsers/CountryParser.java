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
import com.graphhopper.routing.util.spatialrules.CustomArea;
import com.graphhopper.storage.IntsRef;

import java.util.Collections;
import java.util.List;

public class CountryParser implements TagParser {
    private final EnumEncodedValue<NewCountry> countryEnc;

    public CountryParser() {
        this.countryEnc = NewCountry.create();
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
       registerNewEncodedValue.add(countryEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, boolean ferry, IntsRef relationFlags) {
        return handleWayTags(edgeFlags, way, ferry, relationFlags, Collections.emptyList());
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, boolean ferry, IntsRef relationFlags, List<CustomArea> customAreas) {
        for (CustomArea customArea : customAreas) {
            Object countryCode = customArea.getProperties().get("ISO3166-1:alpha3");
            if (countryCode != null) {
                NewCountry country = NewCountry.valueOf(countryCode.toString());
                countryEnc.setEnum(false, edgeFlags, country);
            }
        }
        return edgeFlags;
    }
}
