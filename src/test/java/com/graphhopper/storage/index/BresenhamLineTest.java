/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
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
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author Peter Karich
 */
public class BresenhamLineTest {

    final PointList points = new PointList();
    PointEmitter emitter = new PointEmitter() {
        @Override public void set(double lat, double lon) {
            points.add(lat, lon);
        }
    };

    @Before
    public void setUp() {
        points.clear();
    }

    @Test
    public void testBresenhamLineLeftDown() {
        BresenhamLine.calcPoints(2, 1, -3, -1, emitter, 1, 1);
        assertEquals(Helper.createPointList(2, 1, 1, 1, 0, 0, -1, 0, -2, -1, -3, -1), points);
    }

    @Test
    public void testBresenhamLineLeftUp() {
        BresenhamLine.calcPoints(2, 1, 3, -1, emitter, 1, 1);
        assertEquals(Helper.createPointList(2, 1, 2, 0, 3, -1), points);
    }

    @Test
    public void testBresenhamBug() {
        BresenhamLine.calcPoints(0.5, -0.5, -0.6, 1.6, emitter, 0.75, 1.3);
        assertEquals(Helper.createPointList(0.5, -0.5, -0.25, 0.8, -0.25, 2.1, -1, 3.4), points);
    }
}
