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

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.util.EdgeIteratorState;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.graphhopper.routing.weighting.custom.GeoToValueEntry.AREA_PREFIX;
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
            String maxSpeedKey = "max_speed." + key;
            Object value = entry.getValue();
            if (value == null)
                throw new IllegalArgumentException("Missing value for " + key + " in 'priority'");

            if (key.startsWith(AREA_PREFIX)) {
                if (!(value instanceof Number))
                    throw new IllegalArgumentException(maxSpeedKey + ": area entry requires number value but was: " + value.getClass().getSimpleName());
                Geometry geometry = GeoToValueEntry.pickGeometry(customModel, key);
                maxSpeedList.add(GeoToValueEntry.create(maxSpeedKey, new PreparedGeometryFactory().create(geometry),
                        (Number) value, maxSpeed, 0, maxSpeed));
            } else {
                if (!(value instanceof Map))
                    throw new IllegalArgumentException(maxSpeedKey + ": non-root entries require a map but was: " + value.getClass().getSimpleName());
                final double defaultMaxSpeed = maxSpeed, minMaxSpeed = 0, maxMaxSpeed = maxSpeed;
                EncodedValue encodedValue = getEV(lookup, "max_speed", key);
                if (encodedValue instanceof EnumEncodedValue) {
                    maxSpeedList.add(EnumToValueEntry.create(maxSpeedKey, (EnumEncodedValue) encodedValue,
                            (Map) value, defaultMaxSpeed, minMaxSpeed, maxMaxSpeed));
                } else if (encodedValue instanceof DecimalEncodedValue) {
                    maxSpeedList.add(DecimalToValueEntry.create(maxSpeedKey, (DecimalEncodedValue) encodedValue,
                            (Map) value, defaultMaxSpeed, minMaxSpeed, maxMaxSpeed));
                } else if (encodedValue instanceof BooleanEncodedValue) {
                    maxSpeedList.add(BooleanToValueEntry.create(maxSpeedKey, (BooleanEncodedValue) encodedValue,
                            (Map) value, defaultMaxSpeed, minMaxSpeed, maxMaxSpeed));
                } else if (encodedValue instanceof IntEncodedValue) {
                    maxSpeedList.add(IntToValueEntry.create(maxSpeedKey, (IntEncodedValue) encodedValue,
                            (Map) value, defaultMaxSpeed, minMaxSpeed, maxMaxSpeed));
                } else {
                    throw new IllegalArgumentException("The encoded value '" + key + "' used in 'max_speed' is of type "
                            + encodedValue.getClass().getSimpleName() + ", but only types enum, decimal and boolean are supported.");
                }
            }
        }

        // use speed_factor to reduce the estimated speed value under the specified conditions
        for (Map.Entry<String, Object> entry : customModel.getSpeedFactor().entrySet()) {
            String key = entry.getKey();
            String speedFactorKey = "speed_factor." + key;
            Object value = entry.getValue();
            if (value == null)
                throw new IllegalArgumentException("Missing value for " + key + " in 'priority'");

            if (key.startsWith(AREA_PREFIX)) {
                if (!(value instanceof Number))
                    throw new IllegalArgumentException(speedFactorKey + ": area entry requires number value but was: " + value.getClass().getSimpleName());
                Geometry geometry = GeoToValueEntry.pickGeometry(customModel, key);
                speedFactorList.add(GeoToValueEntry.create(speedFactorKey, new PreparedGeometryFactory().create(geometry),
                        (Number) value, 1, 0, 1));
            } else {
                if (!(value instanceof Map))
                    throw new IllegalArgumentException(speedFactorKey + ": non-root entries require a map but was: " + value.getClass().getSimpleName());
                final double defaultSpeedFactor = 1, minSpeedFactor = 0, maxSpeedFactor = 1;
                EncodedValue encodedValue = getEV(lookup, "speed_factor", key);
                if (encodedValue instanceof EnumEncodedValue) {
                    speedFactorList.add(EnumToValueEntry.create(speedFactorKey, (EnumEncodedValue) encodedValue,
                            (Map) value, defaultSpeedFactor, minSpeedFactor, maxSpeedFactor));
                } else if (encodedValue instanceof DecimalEncodedValue) {
                    speedFactorList.add(DecimalToValueEntry.create(speedFactorKey, (DecimalEncodedValue) encodedValue,
                            (Map) value, defaultSpeedFactor, minSpeedFactor, maxSpeedFactor));
                } else if (encodedValue instanceof BooleanEncodedValue) {
                    speedFactorList.add(BooleanToValueEntry.create(speedFactorKey, (BooleanEncodedValue) encodedValue,
                            (Map) value, defaultSpeedFactor, minSpeedFactor, maxSpeedFactor));
                } else if (encodedValue instanceof IntEncodedValue) {
                    speedFactorList.add(IntToValueEntry.create(speedFactorKey, (IntEncodedValue) encodedValue,
                            (Map) value, defaultSpeedFactor, minSpeedFactor, maxSpeedFactor));
                } else {
                    throw new IllegalArgumentException("The encoded value '" + key + "' used in 'speed_factor' is of type "
                            + encodedValue.getClass().getSimpleName() + ", but only types enum, decimal and boolean are supported.");
                }
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
