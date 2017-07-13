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
package com.graphhopper.routing.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class EncodedValue08Test {
    @Test
    public void testSetValue() {
        EncodedValue08 instance = new EncodedValue08("test", 6, 4, 1, 5, 10);
        assertEquals(10, instance.getValue(instance.setValue(0, 10)));

        instance = new EncodedValue08("test", 0, 4, 1, 5, 10);
        assertEquals(10, instance.getValue(instance.setValue(0, 10)));

        instance = new EncodedValue08("test", 0, 4, 1, 5, 10);
        assertEquals(5, instance.getValue(instance.setDefaultValue(0)));
    }

    @Test
    public void testSwap() {
        EncodedValue08 instance1 = new EncodedValue08("test1", 0, 10, 1, 5, 1000);
        EncodedValue08 instance2 = new EncodedValue08("test2", 10, 10, 1, 5, 1000);
        long flags = instance2.setValue(instance1.setValue(0, 13), 874);
        long swappedFlags = instance1.setValue(instance2.setValue(0, 13), 874);
        assertEquals(swappedFlags, instance1.swap(flags, instance2));
    }
}
