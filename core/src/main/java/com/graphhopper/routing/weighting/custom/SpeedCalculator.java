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

import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.util.EdgeIteratorState;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.graphhopper.routing.weighting.custom.GeoToValueEntry.AREA_PREFIX;
import static com.graphhopper.routing.weighting.custom.PriorityCalculator.createEnumToDoubleArray;
import static com.graphhopper.routing.weighting.custom.PriorityCalculator.getEV;

final class SpeedCalculator {
    private final List<EdgeToValueEntry> speedFactorList = new ArrayList<>();
    private final List<EdgeToValueEntry> maxSpeedList = new ArrayList<>();
    private final DecimalEncodedValue avgSpeedEnc;
    private final double maxSpeed;
    private final double maxSpeedFallback;

    public SpeedCalculator(final double maxSpeed, CustomModel customModel, DecimalEncodedValue avgSpeedEnc,
                           EncodedValueLookup lookup) {
        this.maxSpeed = maxSpeed;
        this.maxSpeedFallback = customModel.getMaxSpeedFallback() == null ? maxSpeed : customModel.getMaxSpeedFallback();
        this.avgSpeedEnc = avgSpeedEnc;
        if (this.maxSpeedFallback > maxSpeed)
            throw new IllegalArgumentException("max_speed_fallback cannot be bigger than max_speed " + maxSpeed);

        // use max_speed to lower speed for the specified conditions
        for (Map.Entry<String, Object> entry : customModel.getMaxSpeed().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Number) {
                double number = ((Number) value).doubleValue();
                if (number > maxSpeed)
                    throw new IllegalArgumentException(key + " cannot be bigger than " + maxSpeed + ", was " + number);

                if (key.startsWith(AREA_PREFIX)) {
                    Geometry geometry = GeoToValueEntry.pickGeometry(customModel, key);
                    maxSpeedList.add(new GeoToValueEntry(new PreparedGeometryFactory().create(geometry), number, maxSpeed));
                } else {
                    throw new IllegalArgumentException("encoded value in 'max_speed' requires a value or range not a number: " + value);
                }
            } else if (value instanceof Map) {
                // TODO NOW check values of number like we do for area!
                double number = 0;
                EncodedValue encodedValue = getEV(lookup, "max_speed", key);
                if (encodedValue instanceof EnumEncodedValue) {
                    EnumEncodedValue enumEncodedValue = (EnumEncodedValue) encodedValue;
                    double[] values = createEnumToDoubleArray("max_speed." + key, maxSpeed, 0, maxSpeed,
                            enumEncodedValue.getValues(), (Map<String, Object>) value);
                    maxSpeedList.add(new EnumToValueEntry(enumEncodedValue, values));
                } else if (encodedValue instanceof DecimalEncodedValue) {
                    maxSpeedList.add(DecimalToValueEntry.create((DecimalEncodedValue) encodedValue,
                            "max_speed." + key, 1, 0, 1, (Map<String, Object>) value));
                } else if (encodedValue instanceof BooleanEncodedValue) {
                    maxSpeedList.add(new BooleanToValueEntry((BooleanEncodedValue) encodedValue, number, maxSpeed));
                } else if (encodedValue instanceof IntEncodedValue) {
                    // TODO NOW
                } else {
                    throw new IllegalArgumentException("encoded value class '" + encodedValue.getClass().getSimpleName()
                            + "' not supported. For '" + key + "' specified in 'max_speed'.");
                }
            } else {
                throw new IllegalArgumentException("Type " + value.getClass() + " is not supported for 'max_speed'");
            }
        }

        // use speed_factor to reduce the estimated speed value under the specified conditions
        for (Map.Entry<String, Object> entry : customModel.getSpeedFactor().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Number) {
                double number = ((Number) value).doubleValue();
                if (number > 1)
                    throw new IllegalArgumentException(key + " cannot be bigger than 1, was " + number);

                if (key.startsWith(AREA_PREFIX)) {
                    Geometry geometry = GeoToValueEntry.pickGeometry(customModel, key);
                    speedFactorList.add(new GeoToValueEntry(new PreparedGeometryFactory().create(geometry), ((Number) value).doubleValue(), 1));
                } else {
                    throw new IllegalArgumentException("encoded value in 'speed_factor' requires a value or range not a number: " + value);
                }
            } else if (value instanceof Map) {
                // TODO NOW check values of number like we do for area!
                double number = 0;
                EncodedValue encodedValue = getEV(lookup, "speed_factor", key);
                if (encodedValue instanceof EnumEncodedValue) {
                    EnumEncodedValue enumEncodedValue = (EnumEncodedValue) encodedValue;
                    double[] values = createEnumToDoubleArray("speed_factor." + key, 1, 0, 1,
                            enumEncodedValue.getValues(), (Map<String, Object>) value);
                    speedFactorList.add(new EnumToValueEntry(enumEncodedValue, values));
                } else if (encodedValue instanceof DecimalEncodedValue) {
                    speedFactorList.add(DecimalToValueEntry.create((DecimalEncodedValue) encodedValue,
                            "speed_factor." + key, 1, 0, 1, (Map<String, Object>) value));
                } else if (encodedValue instanceof BooleanEncodedValue) {
                    speedFactorList.add(new BooleanToValueEntry((BooleanEncodedValue) encodedValue, number, 1));
                } else if (encodedValue instanceof IntEncodedValue) {
                    // TODO NOW
                } else {
                    throw new IllegalArgumentException("encoded value class '" + encodedValue.getClass().getSimpleName()
                            + "' not supported. For '" + key + "' specified in 'speed_factor'.");
                }
            } else {
                throw new IllegalArgumentException("Type " + value.getClass() + " is not supported for 'speed_factor'");
            }
        }
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    /**
     * @return speed in km/h
     */
    public double calcSpeed(EdgeIteratorState edge, boolean reverse) {
        double speed = reverse ? edge.getReverse(avgSpeedEnc) : edge.get(avgSpeedEnc);
        if (Double.isInfinite(speed) || Double.isNaN(speed) || speed < 0)
            throw new IllegalStateException("Invalid estimated speed " + speed);

        for (int i = 0; i < speedFactorList.size(); i++) {
            EdgeToValueEntry entry = speedFactorList.get(i);
            double factorValue = entry.getValue(edge, reverse);
            speed *= factorValue;
            if (speed == 0) break;
        }

        boolean applied = false;
        for (int i = 0; i < maxSpeedList.size(); i++) {
            EdgeToValueEntry entry = maxSpeedList.get(i);
            double maxValue = entry.getValue(edge, reverse);
            if (speed > maxValue) {
                applied = true;
                speed = maxValue;
            }
        }

        if (!applied && speed > maxSpeedFallback)
            return maxSpeedFallback;

        return Math.min(speed, maxSpeed);
    }
}
