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
package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.util.EdgeIteratorState;

import java.util.*;

final class DecimalToValueEntry implements EdgeToValueEntry {
    private final DecimalEncodedValue dev;
    private final double[] ranges;
    private final double[] values;
    private final double fallback;

    /**
     * The ranges array defines intervals where the different values should be picked. The first range implicitly starts with 0.
     * E.g. ranges = [0.5, 1.0] and values = [0.3, 0.6] mean that for values smaller 0.5 the value 0.3 should be picked and
     * for [0.5, 1) it should be 0.6. For everything else it should be the parameter fallback.
     */
    public DecimalToValueEntry(DecimalEncodedValue dev, double[] ranges, double[] values, double fallback) {
        this.dev = dev;
        this.ranges = ranges;
        this.values = values;
        this.fallback = fallback;
        if (ranges.length != values.length)
            throw new IllegalStateException("Ranges count " + ranges.length + " must be equal to values count " + values.length);
    }

    static class Range {
        final double min, max, value;

        public Range(double min, double max, double value) {
            this.min = min;
            this.max = max;
            this.value = value;
            if (max < min)
                throw new IllegalArgumentException("Wrong range. Minimum " + min + " cannot be bigger than maximum " + max);
        }

        @Override
        public String toString() {
            return "min=" + min + ", max=" + max + ", value=" + value;
        }
    }

    static double[][] createArrays(DecimalEncodedValue dev, String name, double defaultValue, double minValue, double maxValue,
                                   Map<String, Object> map) {
        List<Range> ranges = new ArrayList<>();
        // create ranges
        for (Map.Entry<String, Object> encValEntry : map.entrySet()) {
            if (encValEntry.getKey() == null)
                throw new IllegalArgumentException("key for " + name + " cannot be null, value: " + encValEntry.getValue());
            if (encValEntry.getValue() == null)
                throw new IllegalArgumentException("value for " + name + " cannot be null, key: " + encValEntry.getKey());

            Range range = parseRange(name, encValEntry.getKey(), encValEntry.getValue());
            if (range.min < minValue)
                throw new IllegalArgumentException(name + " cannot be smaller than " + minValue + ", minimum of range was " + range.min);
            if (range.max > maxValue)
                throw new IllegalArgumentException(name + " cannot be bigger than " + maxValue + ", maximum of range was " + range.max);
            ranges.add(range);
        }
        // do sort and check separate as we do not want to rely on the order returned from JSON/Yaml/Map
        Collections.sort(ranges, new Comparator<Range>() {
            @Override
            public int compare(Range range, Range range2) {
                return Double.compare(range.min, range2.min);
            }
        });
        Range previousRange = new Range(0, 0, defaultValue);
        List<Double> rangesArray = new ArrayList<>();
        List<Double> valuesArray = new ArrayList<>();
        for (Range range : ranges) {
            if (range.min < previousRange.max)
                throw new IllegalArgumentException("ranges overlap '" + previousRange + "' vs. '" + range + "'");
            if (range.min > previousRange.max && range.value != previousRange.value) {
                rangesArray.add(range.min);
                valuesArray.add(defaultValue);
            }

            rangesArray.add(range.max);
            valuesArray.add(range.value);
        }

        return new double[][]{toDoubleArray(rangesArray), toDoubleArray(valuesArray)};
    }

    static EdgeToValueEntry create(DecimalEncodedValue dev, String name, double defaultValue, double minValue, double maxValue,
                                   Map<String, Object> map) {
        double[][] arrays = createArrays(dev, name, defaultValue, minValue, maxValue, map);
        return new DecimalToValueEntry(dev, arrays[0], arrays[1], defaultValue);
    }

    static double[] toDoubleArray(List<Double> list) {
        double[] arr = new double[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    static Range parseRange(String name, String rangeAsString, Object value) {
        if (!(value instanceof Number))
            throw new IllegalArgumentException(name + " value has to be a number but was " + value);

        String[] strs = rangeAsString.split(",");
        if (strs.length != 2)
            throw new IllegalArgumentException("Range is invalid. It must have exactly two numbers (comma separated). E.g. 1,2.5 but was: " + rangeAsString);
        try {
            return new Range(Double.parseDouble(strs[0].trim()), Double.parseDouble(strs[1].trim()), ((Number) value).doubleValue());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Illegal range " + rangeAsString);
        }
//        int commaIndex = rangeAsString.indexOf(",");
//        int endBracketIndex = rangeAsString.indexOf("]");
//        if (!rangeAsString.startsWith("[") || endBracketIndex != rangeAsString.length() - 1 || commaIndex < 0 || commaIndex > endBracketIndex)
//            throw new IllegalArgumentException("Range is invalid. It must begin with [ and end with ] and have exactly two comma separated numbers. E.g. [1,2.5]");
//        try {
//            String minStr = rangeAsString.substring(1, commaIndex).trim();
//            String maxStr = rangeAsString.substring(commaIndex + 1, endBracketIndex).trim();
//            return new Range(Double.parseDouble(minStr), Double.parseDouble(maxStr), ((Number) value).doubleValue());
//        } catch (Exception ex) {
//            throw new IllegalArgumentException("Illegal range " + rangeAsString);
//        }
    }

    @Override
    public double getValue(EdgeIteratorState iter, boolean reverse) {
        double value = iter.get(dev);
        // TODO PERFORMANCE could we make this faster via a binary search? probably only for many ranges
        for (int i = 0; i < ranges.length; i++) {
            if (value < ranges[i])
                return values[i];
        }
        return fallback;
    }

    @Override
    public String toString() {
        return dev.getName() + ", ranges: " + Arrays.toString(ranges);
    }
}
