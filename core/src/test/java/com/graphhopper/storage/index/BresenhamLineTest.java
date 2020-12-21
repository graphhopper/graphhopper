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

import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class BresenhamLineTest {
    final PointList points = new PointList(10, false);

    @Before
    public void setUp() {
        points.clear();
    }

    @Test
    public void testBresenhamLineLeftDown() {
        BresenhamLine.bresenham(5, 2, 0, 0, points::add);
        // 5,2, 4,2, 3,2, 3,1, 2,1, 1,1, 0,0
        assertEquals(Helper.createPointList(5, 2, 4, 2, 3, 1, 2, 1, 1, 0, 0, 0), points);
    }

    @Test
    public void testBresenhamLineRightDown() {
        BresenhamLine.bresenham(3, 1, 0, 3, points::add);
        // 3,1, 2,1, 1,1, 1,2, 0,2, 0,3
        assertEquals(Helper.createPointList(3, 1, 2, 2, 1, 2, 0, 3), points);
    }

    @Test
    public void testBresenhamLineLeftUp() {
        BresenhamLine.bresenham(2, 2, 3, 0, points::add);
        // 2,2, 2,1, 2,0, 3,0

        assertEquals(Helper.createPointList(2, 2, 2, 1, 3, 0), points);
    }

    @Test
    public void testBresenhamLineRightUp() {
        BresenhamLine.bresenham(0, 0, 2, 3, points::add);
        // 0,0, 0,1, 1,1, 1,2, 2,2, 2,3
        assertEquals(Helper.createPointList(0, 0, 1, 1, 1, 2, 2, 3), points);
    }

}
