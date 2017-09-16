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

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.TagParserFactory;
import com.graphhopper.storage.IntsRef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class EncodedDoubleValue08Test {
    @Test
    public void testSetDoubleValue() {
        EncodedDoubleValue08 instance = new EncodedDoubleValue08("test", 6, 10, 0.01, 5, 10);
        assertEquals(10.12, instance.getDoubleValue(instance.setDoubleValue(0, 10.12)), 1e-4);
    }

    @Test(expected = IllegalStateException.class)
    public void testIllegalFactorMaxValueCombination() {
        new EncodedDoubleValue08("illegalcombination", 6, 2, 2, 0, 3);
    }

    @Test
    public void testUnsignedRightShift_issue417() {
        EncodedDoubleValue08 speedEncoder = new EncodedDoubleValue08("Speed", 56, 8, 1, 30, 255);
        Long flags = -72057594037927936L;
        assertEquals(255, speedEncoder.getDoubleValue(flags), 0.01);
    }
}
