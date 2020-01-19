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
package com.graphhopper.jackson;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * With this approach we avoid the jackson annotations dependency in core. Another approach without duplication would
 * be a separate CustomModelDeserializer with an ObjectMapper with a SNAKE_CASE as property naming strategy.
 */
interface CustomModelMixIn {

    @JsonProperty("vehicle_max_speed")
    double getVehicleMaxSpeed();

    @JsonProperty("vehicle_weight")
    double getVehicleWeight();

    @JsonProperty("vehicle_width")
    double getVehicleWidth();

    @JsonProperty("vehicle_height")
    double getVehicleHeight();

    @JsonProperty("vehicle_length")
    double getVehicleLength();

    @JsonProperty("min_priority")
    double getMinPriority();

    @JsonProperty("distance_factor")
    double getDistanceFactor();

    @JsonProperty("speed_factor")
    Map<String, Object> getSpeedFactor();

    @JsonProperty("average_speed")
    Map<String, Object> getAverageSpeed();
}
