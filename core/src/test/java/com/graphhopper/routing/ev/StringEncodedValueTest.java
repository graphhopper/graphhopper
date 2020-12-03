package com.graphhopper.routing.ev;

import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

public class StringEncodedValueTest {

    @Test
    public void testInit() {
        StringEncodedValue prop = new StringEncodedValue("country", Arrays.asList("deu", "aut", "che"));
        EncodedValue.InitializerConfig init = new EncodedValue.InitializerConfig();
        assertEquals(2, prop.init(init));
        assertEquals(2, prop.bits);
        assertEquals(0, init.dataIndex);
        assertEquals(0, init.shift);
    }

    @Test
    public void testSingleValue() {
        StringEncodedValue prop = new StringEncodedValue("country", Arrays.asList("none"));
        EncodedValue.InitializerConfig init = new EncodedValue.InitializerConfig();
        assertEquals(1, prop.init(init));
        assertEquals(1, prop.bits);
        assertEquals(0, init.dataIndex);
        assertEquals(0, init.shift);
    }
    
    @Test
    public void testPositiveLookup() {
        StringEncodedValue prop = new StringEncodedValue("country", Arrays.asList("deu", "aut", "che"));
        prop.init(new EncodedValue.InitializerConfig());
        
        IntsRef ref = new IntsRef(1);
        assertEquals(null, prop.getString(false, ref));

        prop.setString(false, ref, "aut");
        assertEquals("aut", prop.getString(false, ref));
        
        prop.setString(false, ref, "deu");
        assertEquals("deu", prop.getString(false, ref));
        
        prop.setString(false, ref, "che");
        assertEquals("che", prop.getString(false, ref));
    }
    
    @Test
    public void testNegativeLookup() {
        StringEncodedValue prop = new StringEncodedValue("country", Arrays.asList("deu", "aut", "che"));
        prop.init(new EncodedValue.InitializerConfig());
        
        IntsRef ref = new IntsRef(1);
        prop.setString(false, ref, "xyz");
        assertEquals(null, prop.getString(false, ref));
    }
}