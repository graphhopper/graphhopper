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

import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class MyIntDequeTest {
    
    @Test
    public void testSmall() {
        MyIntDeque deque = new MyIntDeque(8, 2f);
        assertTrue(deque.isEmpty());
        assertEquals(0, deque.size());
    }
    
    @Test
    public void testPush() {
        MyIntDeque deque = new MyIntDeque(8, 2f);
        
        for (int i = 0; i < 60; i++) {
            deque.push(i);
            assertEquals(i + 1, deque.size());
        }
                
        assertEquals(60, deque.size());
        
        assertEquals(0, deque.pop());
        assertEquals(59, deque.size());
        
        assertEquals(1, deque.pop());
        assertEquals(58, deque.size());
        
        deque.push(2);
        assertEquals(59, deque.size());  
        deque.push(3);
        assertEquals(60, deque.size());  
        
        for (int i = 0; i < 50; i++) {
            assertEquals(i + 2, deque.pop());
        }
        
        assertEquals(10, deque.size());
        assertEquals(14, deque.getCapacity());
        
        deque.push(123);
        assertEquals(11, deque.size());
        
        assertEquals(52, deque.pop());
        assertEquals(10, deque.size());
    }
}
