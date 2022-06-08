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

package com.graphhopper.routing.ev;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EncodedValueSerializerTest {
    @Test
    public void serializationAndDeserialization() {
        List<EncodedValue> encodedValues = new ArrayList<>();
        // add enum, int, decimal and boolean encoded values
        encodedValues.add(new EnumEncodedValue<>(RoadClass.KEY, RoadClass.class));
        encodedValues.add(Lanes.create());
        encodedValues.add(MaxWidth.create());
        encodedValues.add(GetOffBike.create());
        StringEncodedValue namesEnc = new StringEncodedValue("names", 3, Arrays.asList("jim", "joe", "kate"), false);
        encodedValues.add(namesEnc);

        // serialize
        List<String> serializedEVs = new ArrayList<>();
        for (EncodedValue e : encodedValues)
            serializedEVs.add(EncodedValueSerializer.serializeEncodedValue(e));

        // deserialize
        List<EncodedValue> deserializedEVs = new ArrayList<>();
        for (String s : serializedEVs)
            deserializedEVs.add(EncodedValueSerializer.deserializeEncodedValue(s));

        // look, it's all there!
        EnumEncodedValue<RoadClass> deserializedRoadClass = (EnumEncodedValue<RoadClass>) deserializedEVs.get(0);
        IntEncodedValue deserializedLanes = (IntEncodedValue) deserializedEVs.get(1);
        DecimalEncodedValue deserializedMaxWidth = (DecimalEncodedValue) deserializedEVs.get(2);
        BooleanEncodedValue deserializedGetOffBike = (BooleanEncodedValue) deserializedEVs.get(3);
        StringEncodedValue deserializedNames = (StringEncodedValue) deserializedEVs.get(4);
        assertEquals("road_class", deserializedRoadClass.getName());
        assertTrue(Arrays.toString(deserializedRoadClass.getValues()).contains("motorway"));
        assertEquals("lanes", deserializedLanes.getName());
        assertEquals("max_width", deserializedMaxWidth.getName());
        assertEquals("get_off_bike", deserializedGetOffBike.getName());
        assertEquals("names", deserializedNames.getName());
        assertTrue(deserializedNames.getValues().contains("jim"));
    }

    @Test
    void wrongVersion() {
        String serializedEV = "{\"className\":\"com.graphhopper.routing.ev.EnumEncodedValue\",\"name\":\"road_class\",\"bits\":5," +
                "\"min_value\":0,\"max_value\":31,\"negate_reverse_direction\":false,\"store_two_directions\":false," +
                "\"enum_type\":\"com.graphhopper.routing.ev.RoadClass\",\"version\":";
        // this fails, because the version is wrong
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> EncodedValueSerializer.deserializeEncodedValue(serializedEV + "404}"));
        assertTrue(e.getMessage().contains("Version does not match"), e.getMessage());
        // this works
        assertEquals("road_class", EncodedValueSerializer.deserializeEncodedValue(serializedEV + "979560347}").getName());
    }
}