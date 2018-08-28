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

import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class EncodedValueOldTest {
    @Test
    public void testSetValue() {
        EncodedValueOld instance = new EncodedValueOld("test", 6, 4, 1, 5, 10);
        assertEquals(10, instance.getValue(instance.setValue(0, 10)));

        instance = new EncodedValueOld("test", 0, 4, 1, 5, 10);
        assertEquals(10, instance.getValue(instance.setValue(0, 10)));

        instance = new EncodedValueOld("test", 0, 4, 1, 5, 10);
        long flags = instance.setDefaultValue(0);
        assertEquals(5, instance.getValue(flags));
    }
}
