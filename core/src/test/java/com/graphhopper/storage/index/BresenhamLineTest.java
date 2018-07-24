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

import com.graphhopper.geohash.KeyAlgo;
import com.graphhopper.geohash.SpatialKeyAlgo;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class BresenhamLineTest {
    final PointList points = new PointList(10, false);
    PointEmitter emitter = new PointEmitter() {
        @Override
        public void set(double lat, double lon) {
            points.add(lat, lon);
        }
    };

    @Before
    public void setUp() {
        points.clear();
    }

    @Test
    public void testBresenhamLineLeftDown() {
        BresenhamLine.calcPoints(5, 2, 0, 0, emitter);
        // 5,2, 4,2, 3,2, 3,1, 2,1, 1,1, 0,0
        assertEquals(Helper.createPointList(5, 2, 4, 2, 3, 1, 2, 1, 1, 0, 0, 0), points);
    }

    @Test
    public void testBresenhamLineRightDown() {
        BresenhamLine.calcPoints(3, 1, 0, 3, emitter);
        // 3,1, 2,1, 1,1, 1,2, 0,2, 0,3
        assertEquals(Helper.createPointList(3, 1, 2, 2, 1, 2, 0, 3), points);
    }

    @Test
    public void testBresenhamLineLeftUp() {
        BresenhamLine.calcPoints(2, 2, 3, 0, emitter);
        // 2,2, 2,1, 2,0, 3,0

        assertEquals(Helper.createPointList(2, 2, 2, 1, 3, 0), points);
    }

    @Test
    public void testBresenhamLineRightUp() {
        BresenhamLine.calcPoints(0, 0, 2, 3, emitter);
        // 0,0, 0,1, 1,1, 1,2, 2,2, 2,3
        assertEquals(Helper.createPointList(0, 0, 1, 1, 1, 2, 2, 3), points);
    }

    @Test
    public void testBresenhamBug() {
        BresenhamLine.calcPoints(0.5, -0.5, -0.6, 1.6, emitter, -1, -1, 0.75, 1.3);
        assertEquals(Helper.createPointList(0.575, -0.87, -0.175, 0.43, -0.925, 1.73), points);
    }

    @Test
    public void testBresenhamHorizontal() {
        BresenhamLine.calcPoints(.5, -.5, .5, 1, emitter, -1, -1, 0.6, 0.4);
        assertEquals(Helper.createPointList(.26, -.56, .26, -0.16, .26, .24, .26, .64, .26, 1.04), points);
    }

    @Test
    public void testBresenhamVertical() {
        BresenhamLine.calcPoints(-.5, .5, 1, 0.5, emitter, 0, 0, 0.4, 0.6);
        assertEquals(Helper.createPointList(-0.36, .06, 0.04, 0.06, 0.44, 0.06, 0.84, 0.06), points);
    }

    @Test
    public void testRealBresenham() {
        int parts = 4;
        int bits = (int) (Math.log(parts * parts) / Math.log(2));
        double minLon = -1, maxLon = 1.6;
        double minLat = -1, maxLat = 0.5;
        final KeyAlgo keyAlgo = new SpatialKeyAlgo(bits).setBounds(minLon, maxLon, minLat, maxLat);
        double deltaLat = (maxLat - minLat) / parts;
        double deltaLon = (maxLon - minLon) / parts;
        final ArrayList<Long> keys = new ArrayList<>();
        PointEmitter tmpEmitter = new PointEmitter() {
            @Override
            public void set(double lat, double lon) {
                keys.add(keyAlgo.encode(lat, lon));
            }
        };
        keys.clear();
        BresenhamLine.calcPoints(.3, -.3, -0.2, 0.2, tmpEmitter, minLat, minLon,
                deltaLat, deltaLon);
        assertEquals(Arrays.asList(11L, 9L), keys);

        keys.clear();
        BresenhamLine.calcPoints(.3, -.1, -0.2, 0.4, tmpEmitter, minLat, minLon,
                deltaLat, deltaLon);

        // 11, 9, 12
        assertEquals(Arrays.asList(11L, 12L), keys);

        keys.clear();
        BresenhamLine.calcPoints(.5, -.5, -0.1, 0.9, tmpEmitter, minLat, minLon,
                deltaLat, deltaLon);
        // precise: 10, 11, 14, 12
        assertEquals(Arrays.asList(10L, 11L, 12L), keys);
    }

    @Test
    public void testBresenhamToLeft() {
        BresenhamLine.calcPoints(
                47.57383, 9.61984,
                47.57382, 9.61890, emitter, 47, 9, 0.00647, 0.00964);
        assertEquals(points.toString(), 1, points.getSize());
    }
}
