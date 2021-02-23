package com.graphhopper.routing.ev;

import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class StringEncodedValueTest {

    @Test
    public void testInitExact() {
        // 3+1 values -> 2 bits
        StringEncodedValue prop = new StringEncodedValue("country", 3);
        EncodedValue.InitializerConfig init = new EncodedValue.InitializerConfig();
        assertEquals(2, prop.init(init));
        assertEquals(2, prop.bits);
        assertEquals(0, init.dataIndex);
        assertEquals(0, init.shift);
    }

    @Test
    public void testInitRoundUp() {
        // 33+1 values -> 6 bits
        StringEncodedValue prop = new StringEncodedValue("country", 33);
        EncodedValue.InitializerConfig init = new EncodedValue.InitializerConfig();
        assertEquals(6, prop.init(init));
        assertEquals(6, prop.bits);
        assertEquals(0, init.dataIndex);
        assertEquals(0, init.shift);
    }
    
    @Test
    public void testInitSingle() {
        StringEncodedValue prop = new StringEncodedValue("country", 1);
        EncodedValue.InitializerConfig init = new EncodedValue.InitializerConfig();
        assertEquals(1, prop.init(init));
        assertEquals(1, prop.bits);
        assertEquals(0, init.dataIndex);
        assertEquals(0, init.shift);
    }
    
    @Test
    public void testInitTooManyEntries() {
        List<String> values = Arrays.asList("aut", "deu", "che", "fra");
        try {
            new StringEncodedValue("country", 2, values, false);
            fail("The encoded value should only allow 3 entries");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Number of values is higher than the maximum value count"));
        }
    }
    
    @Test
    public void testNull() {
        StringEncodedValue prop = new StringEncodedValue("country", 3);
        prop.init(new EncodedValue.InitializerConfig());
        
        IntsRef ref = new IntsRef(1);
        prop.setString(false, ref, null);
        assertEquals(0, prop.getValues().size());
    }
    
    @Test
    public void testEquals() {
        List<String> values = Arrays.asList("aut", "deu", "che");
        StringEncodedValue small = new StringEncodedValue("country", 3, values, false);
        small.init(new EncodedValue.InitializerConfig());

        StringEncodedValue big = new StringEncodedValue("country", 4, values, false);
        big.init(new EncodedValue.InitializerConfig());
        
        assertNotEquals(small, big);
    }
    
    @Test
    public void testLookup() {
        StringEncodedValue prop = new StringEncodedValue("country", 3);
        prop.init(new EncodedValue.InitializerConfig());
        
        IntsRef ref = new IntsRef(1);
        assertEquals(null, prop.getString(false, ref));
        assertEquals(0, prop.getValues().size());

        prop.setString(false, ref, "aut");
        assertEquals("aut", prop.getString(false, ref));
        assertEquals(1, prop.getValues().size());
        
        prop.setString(false, ref, "deu");
        assertEquals("deu", prop.getString(false, ref));
        assertEquals(2, prop.getValues().size());
        
        prop.setString(false, ref, "che");
        assertEquals("che", prop.getString(false, ref));
        assertEquals(3, prop.getValues().size());
        
        prop.setString(false, ref, "deu");
        assertEquals("deu", prop.getString(false, ref));
        assertEquals(3, prop.getValues().size());
    }
    
    @Test
    public void testStoreTooManyEntries() {
        StringEncodedValue prop = new StringEncodedValue("country", 3);
        prop.init(new EncodedValue.InitializerConfig());
        
        IntsRef ref = new IntsRef(1);
        assertEquals(null, prop.getString(false, ref));

        prop.setString(false, ref, "aut");
        assertEquals("aut", prop.getString(false, ref));
        
        prop.setString(false, ref, "deu");
        assertEquals("deu", prop.getString(false, ref));
        
        prop.setString(false, ref, "che");
        assertEquals("che", prop.getString(false, ref));
        
        try {
            prop.setString(false, ref, "xyz");
            fail("The encoded value should only allow a limited number of values");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().startsWith("Maximum number of values reached for"));
        }
    }
    
    @Test
    public void testToString() {
        StringEncodedValue prop = new StringEncodedValue("country", 3);
        prop.init(new EncodedValue.InitializerConfig());
        
        IntsRef ref1 = new IntsRef(1);
        prop.setString(false, ref1, "che");
        prop.setString(false, ref1, "aut");
        prop.setString(false, ref1, "deu");
        
        assertTrue(prop.toString().endsWith("|values=che;aut;deu"));
        
        StringEncodedValue sameProp = new StringEncodedValue("country", 3);
        sameProp.init(new EncodedValue.InitializerConfig());
        
        IntsRef ref2 = new IntsRef(1);
        sameProp.setString(false, ref2, "che");
        sameProp.setString(false, ref2, "aut");
        sameProp.setString(false, ref2, "deu");
        
        assertEquals(prop.toString(), sameProp.toString());
        
        StringEncodedValue shuffledProp = new StringEncodedValue("country", 3);
        shuffledProp.init(new EncodedValue.InitializerConfig());
        
        IntsRef ref3 = new IntsRef(1);
        shuffledProp.setString(false, ref3, "aut");
        shuffledProp.setString(false, ref3, "che");
        shuffledProp.setString(false, ref3, "deu");
        
        assertNotEquals(prop.toString(), shuffledProp.toString());
    }
    
    @Test
    public void testIllegalCharacters() {
        StringEncodedValue prop = new StringEncodedValue("country", 3);
        prop.init(new EncodedValue.InitializerConfig());
        
        IntsRef ref = new IntsRef(1);
        try {
            prop.setString(false, ref, "ch;e");
            fail();
        } catch (Exception e) {
        }
        
        try {
            prop.setString(false, ref, "|che");
            fail();
        } catch (Exception e) {
        }
        
        try {
            prop.setString(false, ref, "che,");
            fail();
        } catch (Exception e) {
        }
        
        try {
            prop.setString(false, ref, "=che");
            fail();
        } catch (Exception e) {
        }
    }
    
    @Test
    public void testNoOverride() {
        IntEncodedValue prop = new UnsignedIntEncodedValue("custom", 2, false) {
            @Override
            protected SortedMap<String, String> getAdditionalProperties() {
                return new TreeMap<>(Collections.singletonMap("version", "1"));
            }
        };
        prop.init(new EncodedValue.InitializerConfig());
        
        try {
            prop.toString();
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage().startsWith("Overriding basic properties"));
        }
    }
}