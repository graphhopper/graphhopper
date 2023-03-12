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

import com.graphhopper.util.PMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncodedValueSerializerTest {
    @Test
    public void serializationAndDeserialization() {
        List<EncodedValue> encodedValues = new ArrayList<>();
        // add enum, int, decimal and boolean encoded values
        DefaultEncodedValueFactory evFactory = new DefaultEncodedValueFactory();
        encodedValues.add(evFactory.create(RoadClass.KEY, new PMap()));
        encodedValues.add(evFactory.create(Lanes.KEY, new PMap()));
        encodedValues.add(evFactory.create(MaxWidth.KEY, new PMap()));
        encodedValues.add(evFactory.create(GetOffBike.KEY, new PMap()));
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
    void explicitString() {
        EncodedValue.InitializerConfig initializerConfig = new EncodedValue.InitializerConfig();
        DefaultEncodedValueFactory evFactory = new DefaultEncodedValueFactory();
        List<EncodedValue> evs = Arrays.asList(
                evFactory.create(Lanes.KEY, new PMap()),
                evFactory.create(MaxWidth.KEY, new PMap()),
                evFactory.create(GetOffBike.KEY, new PMap())
        );
        evs.forEach(ev -> ev.init(initializerConfig));

        List<String> serialized = evs.stream().map(EncodedValueSerializer::serializeEncodedValue).collect(Collectors.toList());
        assertEquals("{\"className\":\"com.graphhopper.routing.ev.IntEncodedValueImpl\",\"name\":\"lanes\",\"bits\":3," +
                "\"min_storable_value\":0,\"max_storable_value\":7,\"max_value\":-2147483648,\"negate_reverse_direction\":false,\"store_two_directions\":false," +
                "\"fwd_data_index\":0,\"bwd_data_index\":0,\"fwd_shift\":0,\"bwd_shift\":-1,\"fwd_mask\":7,\"bwd_mask\":0}", serialized.get(0));
        assertEquals("{\"className\":\"com.graphhopper.routing.ev.DecimalEncodedValueImpl\",\"name\":\"max_width\",\"bits\":7," +
                "\"min_storable_value\":0,\"max_storable_value\":127,\"max_value\":-2147483648,\"negate_reverse_direction\":false,\"store_two_directions\":false," +
                "\"fwd_data_index\":0,\"bwd_data_index\":0,\"fwd_shift\":3,\"bwd_shift\":-1,\"fwd_mask\":1016,\"bwd_mask\":0," +
                "\"factor\":0.1,\"use_maximum_as_infinity\":true}", serialized.get(1));
        assertEquals("{\"className\":\"com.graphhopper.routing.ev.SimpleBooleanEncodedValue\",\"name\":\"get_off_bike\",\"bits\":1," +
                "\"min_storable_value\":0,\"max_storable_value\":1,\"max_value\":-2147483648,\"negate_reverse_direction\":false,\"store_two_directions\":false,\"fwd_data_index\":0," +
                "\"bwd_data_index\":0,\"fwd_shift\":10,\"bwd_shift\":-1,\"fwd_mask\":1024,\"bwd_mask\":0}", serialized.get(2));

        EncodedValue ev0 = EncodedValueSerializer.deserializeEncodedValue(serialized.get(0));
        assertEquals("lanes", ev0.getName());
        EncodedValue ev1 = EncodedValueSerializer.deserializeEncodedValue(serialized.get(1));
        assertEquals("max_width", ev1.getName());
        EncodedValue ev2 = EncodedValueSerializer.deserializeEncodedValue(serialized.get(2));
        assertEquals("get_off_bike", ev2.getName());
    }

}