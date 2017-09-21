package com.graphhopper.routing.profiles;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonFactory;
import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IntEncodedValueTest {

    final GHJson json = new GHJsonFactory().create();

    @Test
    public void testInvalidReverseAccess() {
        IntEncodedValue prop = new IntEncodedValue("test", 10, 50, false);
        prop.init(new EncodedValue.InitializerConfig());
        try {
            prop.setInt(true, new IntsRef(1), -1);
            assertTrue(false);
        } catch (Exception ex) {
        }
    }

    @Test
    public void testDirectedValue() {
        IntEncodedValue prop = new IntEncodedValue("test", 10, 50, true);
        prop.init(new EncodedValue.InitializerConfig());
        IntsRef ref = new IntsRef(1);
        prop.setInt(false, ref, 10);
        prop.setInt(true, ref, 20);
        assertEquals(10, prop.getInt(false, ref));
        assertEquals(20, prop.getInt(true, ref));
    }

    @Test
    public void multiIntsUsage() {
        IntEncodedValue prop = new IntEncodedValue("test", 32, 50, true);
        prop.init(new EncodedValue.InitializerConfig());
        IntsRef ref = new IntsRef(2);
        prop.setInt(false, ref, 10);
        prop.setInt(true, ref, 20);
        assertEquals(10, prop.getInt(false, ref));
        assertEquals(20, prop.getInt(true, ref));
    }

    @Test
    public void padding() {
        IntEncodedValue prop = new IntEncodedValue("test", 30, 50, true);
        prop.init(new EncodedValue.InitializerConfig());
        IntsRef ref = new IntsRef(2);
        prop.setInt(false, ref, 10);
        prop.setInt(true, ref, 20);
        assertEquals(10, prop.getInt(false, ref));
        assertEquals(20, prop.getInt(true, ref));
    }

    @Test
    public void serialization() {
        IntEncodedValue prop = new IntEncodedValue("test", 30, 50, true);
        prop.init(new EncodedValue.InitializerConfig());
        Map<String, Object> map = new HashMap();
        map.put("name", "test");
        map.put("bits", 30);
        map.put("class_type", prop.getClass().getSimpleName());
        map.put("default_value", 50);
        map.put("store_both_directions", true);
        assertEquals(toMap(json.toJson(map)), toMap(json.toJson(prop)));
    }

    @Test
    public void deserialization() {
        IntEncodedValue prop = new IntEncodedValue("test", 30, 50, true);
        prop.init(new EncodedValue.InitializerConfig());

        assertEquals(prop.bits, json.fromJson(new StringReader(json.toJson(prop)), IntEncodedValue.class).bits);
    }

    Map<String, Object> toMap(String jsonStr) {
        try {
            return new ObjectMapper().readValue(new StringReader(jsonStr), new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}