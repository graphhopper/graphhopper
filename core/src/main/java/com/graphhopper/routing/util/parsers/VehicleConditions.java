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
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Evaluates the non-temporal conditional restrictions ("no @ (weight > 7.5)", "no @ hgv", "none @ delivery") and the
 * dimension limits (maxweight, maxheight, maxwidth, maxlength, maxaxleload) for a vehicle of the given dimensions.
 * Temporal conditions are not evaluated here, see OSMTemporalAccessParser.
 */
public class VehicleConditions {
    private static final List<String> LIMIT_KEYS = List.of("maxweight", "maxheight", "maxwidth");
    private final List<String> liftKeys;
    private final Set<String> allowed;
    private double weight = 2.5, height = 2, width = 1.9, mirrorWidth = 0, length = 4.5;
    private int axes = 2;
    private boolean hgv;

    /**
     * @param allowed the access values that lift a limit, e.g. maxweight:conditional=none @ delivery applies only
     *                if delivery is allowed
     */
    public VehicleConditions(List<String> restrictionKeys, Set<String> allowed) {
        this.allowed = allowed;
        liftKeys = new ArrayList<>(restrictionKeys);
        liftKeys.addAll(LIMIT_KEYS);
    }

    /**
     * @param weight total weight in tons including payload
     */
    public VehicleConditions weight(double weight) {
        this.weight = weight;
        return this;
    }

    public VehicleConditions height(double height) {
        this.height = height;
        return this;
    }

    public VehicleConditions width(double width, double mirrorWidth) {
        this.width = width;
        this.mirrorWidth = mirrorWidth;
        return this;
    }

    public VehicleConditions length(double length) {
        this.length = length;
        return this;
    }

    public VehicleConditions axes(int axes) {
        this.axes = axes;
        return this;
    }

    public VehicleConditions hgv(boolean hgv) {
        this.hgv = hgv;
        return this;
    }

    /**
     * @return the value of key:conditional if its condition applies to this vehicle, otherwise null. E.g. for
     * maxweight:conditional=none @ (weight>7.5) a 12t truck gets "none", a car null.
     */
    public String getConditionalValue(ReaderWay way, String key) {
        String tag = way.getTag(key + ":conditional");
        if (tag == null || tag.contains(";")) return null;
        String[] parts = tag.split("@");
        if (parts.length != 2) return null;
        String condition = parts[1].replace('(', ' ').replace(')', ' ').trim();
        try {
            return conditionApplies(condition) ? parts[0].trim() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean conditionApplies(String condition) {
        // none @ delivery, no @ hgv, none @ maxweight
        if (allowed.contains(condition) || liftKeys.contains(condition)) return true;
        boolean less = true;
        int index = condition.indexOf('<');
        if (index <= 0) {
            index = condition.indexOf('>');
            less = false;
        }
        if (index <= 0) return false;
        double vehicleValue = switch (condition.substring(0, index).trim()) {
            case "weight" -> weight;
            case "height" -> height;
            case "width" -> width;
            default -> Double.NaN;
        };
        if (Double.isNaN(vehicleValue)) return false;
        if (condition.charAt(index + 1) == '=') index++;
        double limit = parseNumber(condition.substring(index + 1));
        return less ? vehicleValue < limit : vehicleValue > limit;
    }

    static double parseNumber(String str) {
        int untilIndex = str.length() - 1;
        for (; untilIndex >= 0; untilIndex--) {
            if (Character.isDigit(str.charAt(untilIndex)))
                break;
        }
        return Double.parseDouble(str.substring(0, untilIndex + 1));
    }

    /**
     * @return true if a maxheight, maxwidth, maxlength, maxweight or maxaxleload tag is below the vehicle dimensions.
     * The weight limit is lifted by a conditional with an allowed value, e.g. maxweight:conditional=none @ destination
     * or access:conditional=yes @ (weight > 7.5)
     */
    public boolean exceedsLimits(ReaderWay way) {
        // bridges often tag their own height instead of a limit, https://discuss.graphhopper.com/t/1468
        if (!way.hasTag("bridge") && (meter(way, "maxheight") < height || meter(way, "maxheight:physical") < height
                || meter(way, "height") < height + 0.3))
            return true;
        double tmpWidth = width + mirrorWidth;
        if (meter(way, "maxwidth") < tmpWidth || meter(way, "maxwidth:physical") < tmpWidth || meter(way, "width") < tmpWidth + 0.05)
            return true;
        if (meter(way, "maxlength") < length || hgv && meter(way, "maxlength:hgv") < length)
            return true;
        if ((tons(way, "maxweight") < weight || tons(way, "maxgcweight") < weight) && !limitLifted(way))
            return true;
        return tons(way, "maxaxleload") < weight / axes;
    }

    private boolean limitLifted(ReaderWay way) {
        for (String key : liftKeys) {
            String value = getConditionalValue(way, key);
            if (value != null && (allowed.contains(value) || "none".equals(value)))
                return true;
        }
        return false;
    }

    private static double meter(ReaderWay way, String key) {
        return OSMValueExtractor.stringToMeter(way.getTag(key, ""));
    }

    private static double tons(ReaderWay way, String key) {
        return OSMValueExtractor.stringToTons(way.getTag(key, ""));
    }
}
