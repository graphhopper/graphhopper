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
package com.graphhopper.storage.index;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class Location2IDFullWithEdgesIndexTest extends AbstractLocationIndexTester {
    @Override
    public LocationIndex createIndex(Graph g, int resolution) {
        return new Location2IDFullWithEdgesIndex(g);
    }

    @Override
    public boolean hasEdgeSupport() {
        return true;
    }

    @Override
    public void testGrid() {
        // do not test against itself
    }

    @Test
    public void testFullIndex() {
        LocationIndex tmpIdx = new Location2IDFullWithEdgesIndex(createSampleGraph(EncodingManager.create("car")));
        assertEquals(5, findID(tmpIdx, 2, 3));
        assertEquals(10, findID(tmpIdx, 4, 1));
        // 6, 9 or 10
        assertEquals(10, findID(tmpIdx, 3.6, 1.4));
    }
}
