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
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.MaxWeight;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static com.graphhopper.util.Helper.isEmpty;
import static com.graphhopper.util.Helper.toLowerCase;

public class OSMMaxWeightParser implements TagParser {

    private static Logger LOG = LoggerFactory.getLogger(OSMMaxWeightParser.class);
    private final DecimalEncodedValue weightEncoder;
    private final boolean enableLog;

    public OSMMaxWeightParser() {
        this(MaxWeight.create(), false);
    }

    public OSMMaxWeightParser(DecimalEncodedValue weightEncoder, boolean enableLog) {
        this.weightEncoder = weightEncoder;
        this.enableLog = enableLog;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        registerNewEncodedValue.add(weightEncoder);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access, long relationFlags) {
        // do not include OSM tag "height" here as it has completely different meaning (height of peak)
        List<String> weightTags = Arrays.asList("maxweight", "maxgcweight");
        extractTons(edgeFlags, way, weightEncoder, weightTags, enableLog);
        return edgeFlags;
    }

    static void extractTons(IntsRef edgeFlags, ReaderWay way, DecimalEncodedValue valueEncoder, List<String> keys, boolean enableLog) {
        String value = way.getFirstPriorityTag(keys);
        if (isEmpty(value))
            return;
        try {
            double val = stringToTons(value);
            if (val > valueEncoder.getMaxDecimal())
                val = valueEncoder.getMaxDecimal();
            valueEncoder.setDecimal(false, edgeFlags, val);
        } catch (Exception ex) {
            if (enableLog)
                LOG.warn("Unable to extract tons from malformed road attribute '{}' for way (OSM_ID = {}).", value, way.getId());
        }
    }

    public static double stringToTons(String value) {
        value = toLowerCase(value).replaceAll(" ", "").replaceAll("(tons|ton)", "t");
        value = value.replace("mgw", "").trim();
        double factor = 1;
        if (value.equals("default") || value.equals("none")) {
            return -1;
        } else if (value.endsWith("t")) {
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("lbs")) {
            value = value.substring(0, value.length() - 3);
            factor = 0.00045359237;
        }

        return Double.parseDouble(value) * factor;
    }
}
