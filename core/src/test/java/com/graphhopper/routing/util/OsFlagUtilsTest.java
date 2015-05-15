package com.graphhopper.routing.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.graphhopper.reader.Way;

public class OsFlagUtilsTest {

    private Way testWay = null;

    @Before
    public void setup() {
        testWay = new Way() {
            private final Map<String, Object> properties = new HashMap<>();

            @Override
            public void setTag(String name, Object value) {
                properties.put(name, value);
            }

            @Override
            public String getTag(String name) {
                Object object = properties.get(name);
                return (null != object) ? (String) object.toString() : null;
            }

            @Override
            public <T> T getTag(String key, T defaultValue) {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public boolean hasTags() {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public boolean hasTag(String key, String... values) {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public boolean hasTag(String key, Object value) {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public boolean hasTag(String key, Set<String> values) {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public boolean hasTag(List<String> keyList, Set<String> values) {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public int getType() {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public boolean isType(int way) {
                // TODO Auto-generated method stub
                return false;
            }

        };
    }

    @Test
    public void testHasTag_singleValue() {
        testWay.setTag("key", "value1");
        assertTrue("value1 should be in key", OsFlagUtils.hasTag(testWay, "key", "value1"));
    }

    @Test
    public void testHasTag_multipleValues() {
        testWay.setTag("key", "value1,value2");
        assertTrue("value1 should be in key", OsFlagUtils.hasTag(testWay, "key", "value1"));
        assertTrue("value2 should be in key", OsFlagUtils.hasTag(testWay, "key", "value2"));
        assertFalse("value3 should NOT be in key", OsFlagUtils.hasTag(testWay, "key", "value3"));
    }

    @Test
    public void testSetOrAppendTag_singleValue() {
        OsFlagUtils.setOrAppendTag(testWay, "key", "value1");
        OsFlagUtils.setOrAppendTag(testWay, "key", "value2");
        assertEquals("value1,value2 should be in key", testWay.getTag("key"), "value1,value2");
    }

    @Test
    public void testSetOrAppendTag_multipleValues() {
        OsFlagUtils.setOrAppendTag(testWay, "key", "value1");
        assertEquals("value1 should be in key", testWay.getTag("key"), "value1");
    }
}
