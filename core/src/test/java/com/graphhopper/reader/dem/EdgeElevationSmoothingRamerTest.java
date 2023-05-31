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
package com.graphhopper.reader.dem;

import com.graphhopper.util.PointList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Karich
 */
public class EdgeElevationSmoothingRamerTest {
    @Test
    public void smoothRamer() {
        PointList pl1 = new PointList(3, true);
        pl1.add(0, 0, 0);
        pl1.add(0.0005, 0.0005, 100);
        pl1.add(0.001, 0.001, 50);
        EdgeElevationSmoothingRamer.smooth(pl1, 70);
        assertEquals(3, pl1.size());
        assertEquals(100, pl1.getEle(1), .1);
        EdgeElevationSmoothingRamer.smooth(pl1, 75);
        assertEquals(3, pl1.size());
        assertEquals(25, pl1.getEle(1), .1);
    }

    @Test
    public void smoothRamer2() {
        PointList pl2 = new PointList(3, true);
        pl2.add(0.001, 0.001, 50);
        pl2.add(0.0015, 0.0015, 160);
        pl2.add(0.0016, 0.0015, 150);
        pl2.add(0.0017, 0.0015, 220);
        pl2.add(0.002, 0.002, 20);
        EdgeElevationSmoothingRamer.smooth(pl2, 100);
        assertEquals(5, pl2.size());
        assertEquals(190, pl2.getEle(1), 1); // modify as too small in interval [0,4]
        assertEquals(210, pl2.getEle(2), 1); // modify as too small in interval [0,4]
        assertEquals(220, pl2.getEle(3), .1); // keep as it is bigger than maxElevationDelta in interval [0,4]
    }

    @Test
    public void smoothRamerNoMaximumFound() {
        PointList pl2 = new PointList(3, true);
        pl2.add(60.03307, 20.82262, 5.35);
        pl2.add(60.03309, 20.82269, 5.42);
        pl2.add(60.03307, 20.82262, 5.35);
        EdgeElevationSmoothingRamer.smooth(pl2, 10);
        assertEquals(3, pl2.size());
        assertEquals(5.35, pl2.getEle(0), 0.01);
        assertEquals(5.35, pl2.getEle(1), 0.01);
        assertEquals(5.35, pl2.getEle(2), 0.01);
    }
}
