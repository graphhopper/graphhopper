package com.graphhopper.routing.ev;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnumEncodedValueTest {

    @Test
    public void testInit() {
        EnumEncodedValue<RoadClass> prop = new EnumEncodedValue<>("road_class", RoadClass.class);
        EncodedValue.InitializerConfig init = new EncodedValue.InitializerConfig();
        assertEquals(5, prop.init(init));
        assertEquals(5, prop.bits);
        assertEquals(0, init.dataIndex);
        assertEquals(0, init.shift);
        IntsRef ref = new IntsRef(1);
        // default if empty
        ref.ints[0] = 0;
        assertEquals(RoadClass.OTHER, prop.getEnum(false, ref));

        prop.setEnum(false, ref, RoadClass.SECONDARY);
        assertEquals(RoadClass.SECONDARY, prop.getEnum(false, ref));
    }

    @Test
    public void testSize() {
        assertEquals(3, 32 - Integer.numberOfLeadingZeros(7 - 1));
        assertEquals(3, 32 - Integer.numberOfLeadingZeros(8 - 1));
        assertEquals(4, 32 - Integer.numberOfLeadingZeros(9 - 1));
        assertEquals(4, 32 - Integer.numberOfLeadingZeros(16 - 1));
        assertEquals(5, 32 - Integer.numberOfLeadingZeros(17 - 1));
    }

    @Test
    public void serializationAndDeserialization() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        EnumEncodedValue<RoadClass> roadClass = new EnumEncodedValue<>(RoadClass.KEY, RoadClass.class);
        String roadClassString = mapper.writeValueAsString(roadClass);
        System.out.println(roadClassString);
        EnumEncodedValue encodedValue = mapper.readValue(roadClassString, EnumEncodedValue.class);
        EnumEncodedValue<RoadClass> roadClass2 = (EnumEncodedValue) encodedValue;
        System.out.println(roadClass2);
    }

}