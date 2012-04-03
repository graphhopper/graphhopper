/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class BitUtilTest {

    @Test
    public void testToFloat() {
        byte[] bytes = BitUtil.fromFloat(Float.MAX_VALUE);
        assertEquals(Float.MAX_VALUE, BitUtil.toFloat(bytes), 1e-9);
        
        bytes = BitUtil.fromFloat(Float.MAX_VALUE / 3);
        assertEquals(Float.MAX_VALUE / 3, BitUtil.toFloat(bytes), 1e-9);
    }
    
    @Test
    public void testToInt() {
        byte[] bytes = BitUtil.fromInt(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, BitUtil.toInt(bytes));
        
        bytes = BitUtil.fromInt(Integer.MAX_VALUE / 3);
        assertEquals(Integer.MAX_VALUE / 3, BitUtil.toInt(bytes));
    }
    
    @Test
    public void testToLong() {
        byte[] bytes = BitUtil.fromLong(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, BitUtil.toLong(bytes));
        
        bytes = BitUtil.fromLong(Long.MAX_VALUE / 7);
        assertEquals(Long.MAX_VALUE / 7, BitUtil.toLong(bytes));
    }
    
    @Test
    public void testToBitString() {        
        assertEquals("0010101010101010101010101010101010101010101010101010101010101010", BitUtil.toBitString(Long.MAX_VALUE / 3));
        assertEquals("0111111111111111111111111111111111111111111111111111111111111111", BitUtil.toBitString(Long.MAX_VALUE));
                
        assertEquals("00101010101010101010101010101010", BitUtil.toBitString(BitUtil.fromInt(Integer.MAX_VALUE / 3)));
        
        assertEquals("10000000000000000000000000000000", BitUtil.toBitString((1L << 31), 32));        
        assertEquals("00000000000000000000000000000001", BitUtil.toBitString(1, 32));
    }
}
