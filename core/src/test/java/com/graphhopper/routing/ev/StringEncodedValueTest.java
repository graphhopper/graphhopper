package com.graphhopper.routing.ev;

import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
}