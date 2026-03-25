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
package com.graphhopper.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class RAMInt1SegmentDataAccessTest extends DataAccessTest {
    @Override
    public DataAccess createDataAccess(String name, int segmentSize) {
        return new RAMInt1SegmentDataAccess(name, directory, true, segmentSize);
    }

    @Override
    @Test
    public void testSet_GetBytes() {
    }

    @Override
    @Test
    public void testSet_GetByte() {
    }

    @Override
    @Test
    public void testSet_Get_Short_Long() {
    }
}
