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
package com.graphhopper.routing.profiles;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FlexConfig {

    /**
     * Converting to seconds is not necessary but makes adding other penalties easier (e.g. turn
     * costs or traffic light costs etc)
     */
    public final static double SPEED_CONV = 3.6;
    // required
    private String base;
    private double maxSpeed;
    // optional:
    private double maxPriority = 10;
    private Double weight, width, height, length;
    // max_priority and max_speed have a significant influence on the min_weight estimate, i.e. on quality vs. speed for A* with beeline
    // it also limits possibility to prefer a road
    private double distanceFactor = 1;
    private Map<String, Object> speedFactor = Collections.emptyMap();
    private Map<String, Object> averageSpeed = Collections.emptyMap();

    public FlexConfig() {
    }

    public String getBase() {
        if (Helper.isEmpty(base))
            throw new IllegalArgumentException("No base specified");
        return base;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public Double getWeight() {
        return weight;
    }

    public void setHeight(Double height) {
        this.height = height;
    }

    public Double getHeight() {
        return height;
    }

    public void setLength(Double length) {
        this.length = length;
    }

    public Double getLength() {
        return length;
    }

    public void setWidth(Double width) {
        this.width = width;
    }

    public Double getWidth() {
        return width;
    }

    public void setMaxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    @JsonProperty("max_speed")
    public double getMaxSpeed() {
        if (maxSpeed < 2)
            throw new IllegalArgumentException("max_speed must be at least 2km/h");
        return maxSpeed;
    }

    public void setMaxPriority(double maxPriority) {
        this.maxPriority = maxPriority;
    }

    @JsonProperty("max_priority")
    public double getMaxPriority() {
        return maxPriority;
    }

    public void setDistanceFactor(double distanceFactor) {
        this.distanceFactor = distanceFactor;
    }

    @JsonProperty("distance_factor")
    public double getDistanceFactor() {
        return distanceFactor;
    }

    @JsonProperty("speed_factor")
    public Map<String, Object> getSpeedFactor() {
        return speedFactor;
    }

    @JsonProperty("average_speed")
    public Map<String, Object> getAverageSpeed() {
        return averageSpeed;
    }

    public AverageSpeedConfig createAverageSpeedConfig(EncodedValueLookup lookup, EncodedValueFactory factory) {
        return new AverageSpeedConfig(this, lookup, factory);
    }

    public DelayFlexConfig createDelayConfig(EncodedValueLookup lookup, EncodedValueFactory factory) {
        return new DelayFlexConfig(this, lookup, factory);
    }

    public PriorityFlexConfig createPriorityConfig(EncodedValueLookup lookup, EncodedValueFactory factory) {
        return new PriorityFlexConfig(this, lookup, factory);
    }

    public static class AverageSpeedConfig {
        private List<ConfigMapEntry> speedFactorList = new ArrayList<>();
        private List<ConfigMapEntry> avgSpeedList = new ArrayList<>();
        private DecimalEncodedValue avgSpeedEnc;
        private FlexConfig flexConfig;

        private AverageSpeedConfig(FlexConfig flexConfig, EncodedValueLookup lookup, EncodedValueFactory factory) {
            this.flexConfig = flexConfig;
            this.avgSpeedEnc = lookup.getDecimalEncodedValue(EncodingManager.getKey(flexConfig.getBase(), "average_speed"));
            // do as much as possible outside of the eval method
            for (Map.Entry<String, Object> entry : flexConfig.getAverageSpeed().entrySet()) {
                Object value = entry.getValue();
                if (!lookup.hasEncodedValue(entry.getKey()))
                    throw new IllegalArgumentException("Cannot find '" + entry.getKey() + "' specified in 'average_speed'");

                if (value instanceof Map) {
                    EnumEncodedValue enumEncodedValue = lookup.getEnumEncodedValue(entry.getKey(), Enum.class);
                    Class<? extends Enum> enumClass = factory.findValues(entry.getKey());
                    avgSpeedList.add(new EnumToValue(enumEncodedValue, createEnumToDoubleArray(enumClass, (Map<String, Object>) value)));
                } else {
                    throw new IllegalArgumentException("Type " + value.getClass() + " is not supported for 'average_speed'");
                }
            }

            for (Map.Entry<String, Object> entry : flexConfig.getSpeedFactor().entrySet()) {
                if (!lookup.hasEncodedValue(entry.getKey()))
                    throw new IllegalArgumentException("Cannot find '" + entry.getKey() + "' specified in 'speed_factor'");
                Object value = entry.getValue();
                if (value instanceof Map) {
                    EnumEncodedValue enumEncodedValue = lookup.getEnumEncodedValue(entry.getKey(), Enum.class);
                    Class<? extends Enum> enumClass = factory.findValues(entry.getKey());
                    speedFactorList.add(new EnumToValue(enumEncodedValue, createEnumToDoubleArray(enumClass, (Map<String, Object>) value)));
                } else {
                    throw new IllegalArgumentException("Type " + value.getClass() + " is not supported for 'speed_factor'");
                }
            }
        }

        /**
         * @return speed in km/h
         */
        public double eval(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
            // this code is interpreting the yaml. We could try to make it faster using ANTLR with which we can create AST and compile using janino
            double speed = Double.NaN;
            for (int i = 0; i < avgSpeedList.size(); i++) {
                ConfigMapEntry entry = avgSpeedList.get(i);
                Double value = entry.getValue(edge, reverse);
                // only first matches
                if (value != null) {
                    speed = ((Number) value).doubleValue();
                    break;
                }
            }
            if (Double.isNaN(speed))
                speed = reverse ? edge.getReverse(avgSpeedEnc) : edge.get(avgSpeedEnc);
            if (speed == 0)
                return 0;
            if (Double.isInfinite(speed) || Double.isNaN(speed) || speed < 0)
                throw new IllegalStateException("Invalid average_speed " + speed);

            for (int i = 0; i < speedFactorList.size(); i++) {
                ConfigMapEntry entry = speedFactorList.get(i);
                Double value = entry.getValue(edge, reverse);
                // include all matches
                if (value != null)
                    speed *= ((Number) value).doubleValue();
            }

            return Math.min(flexConfig.maxSpeed, speed);
        }
    }

    private interface ConfigMapEntry {
        Double getValue(EdgeIteratorState iter, boolean reverse);
    }

    private static class EnumToValue implements ConfigMapEntry {
        EnumEncodedValue eev;
        Double[] values;

        EnumToValue(EnumEncodedValue eev, Double[] values) {
            this.eev = eev;
            this.values = values;
        }

        @Override
        public Double getValue(EdgeIteratorState iter, boolean reverse) {
            Enum enumVal = iter.get(eev);
            return values[enumVal.ordinal()];
        }
    }

    private static class MaxValueConfigMapEntry implements ConfigMapEntry {
        DecimalEncodedValue ev;
        double vehicleValue;

        public MaxValueConfigMapEntry(String name, DecimalEncodedValue ev, double vehicleValue) {
            if (vehicleValue < 0)
                throw new IllegalArgumentException(name + " cannot be negative");
            this.ev = ev;
            this.vehicleValue = vehicleValue;
        }

        @Override
        public Double getValue(EdgeIteratorState iter, boolean reverse) {
            return (vehicleValue < (reverse ? iter.getReverse(ev) : iter.get(ev))) ? null : 0.0;
        }
    }

    public static class DelayFlexConfig {
        private DelayFlexConfig(FlexConfig flexConfig, EncodedValueLookup lookup, EncodedValueFactory factory) {
        }

        /**
         * @return delay in seconds
         */
        public double eval(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
            // TODO include turn costs and ferry cost here
            return 0;
        }
    }

    public static class PriorityFlexConfig {
        private final FlexConfig config;
        private List<ConfigMapEntry> priorityList = new ArrayList<>();

        public PriorityFlexConfig(FlexConfig flexConfig, EncodedValueLookup lookup, EncodedValueFactory factory) {
            this.config = flexConfig;
            if (flexConfig.weight != null && lookup.hasEncodedValue(MaxWeight.KEY))
                priorityList.add(new MaxValueConfigMapEntry("weight", lookup.getDecimalEncodedValue(MaxWeight.KEY), flexConfig.weight));
            if (flexConfig.width != null && lookup.hasEncodedValue(MaxWidth.KEY))
                priorityList.add(new MaxValueConfigMapEntry("width", lookup.getDecimalEncodedValue(MaxWidth.KEY), flexConfig.width));
            if (flexConfig.height != null && lookup.hasEncodedValue(MaxHeight.KEY))
                priorityList.add(new MaxValueConfigMapEntry("height", lookup.getDecimalEncodedValue(MaxHeight.KEY), flexConfig.height));
            if (flexConfig.length != null && lookup.hasEncodedValue(MaxLength.KEY))
                priorityList.add(new MaxValueConfigMapEntry("length", lookup.getDecimalEncodedValue(MaxLength.KEY), flexConfig.length));

            for (Map.Entry<String, Object> entry : flexConfig.getSpeedFactor().entrySet()) {
                if (!lookup.hasEncodedValue(entry.getKey()))
                    throw new IllegalArgumentException("Cannot find '" + entry.getKey() + "' specified in 'priority'");
                Object value = entry.getValue();
                if (value instanceof Map) {
                    EnumEncodedValue enumEncodedValue = lookup.getEnumEncodedValue(entry.getKey(), Enum.class);
                    Class<? extends Enum> enumClass = factory.findValues(entry.getKey());
                    priorityList.add(new EnumToValue(enumEncodedValue, createEnumToDoubleArray(enumClass, (Map<String, Object>) value)));
                } else {
                    throw new IllegalArgumentException("Type " + value.getClass() + " is not supported for 'priority'");
                }
            }
        }

        /**
         * @return weight without unit. The lower it is the higher the priority of the specified edge should be.
         */
        public double eval(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
            double priority = 1;
            for (int i = 0; i < priorityList.size(); i++) {
                ConfigMapEntry entry = priorityList.get(i);
                Double value = entry.getValue(edge, reverse);
                // include all matches
                if (value != null)
                    priority *= ((Number) value).doubleValue();
            }
            return Math.min(priority, config.maxPriority);
        }
    }

    static Double[] createEnumToDoubleArray(Class<? extends Enum> enumClass, Map<String, Object> map) {
        Double[] tmp = new Double[enumClass.getEnumConstants().length];
        for (Map.Entry<String, Object> encValEntry : map.entrySet()) {
            Enum enumValue = Helper.getValueOf(enumClass, encValEntry.getKey());
            tmp[enumValue.ordinal()] = ((Number) encValEntry.getValue()).doubleValue();
        }
        return tmp;
    }
}
