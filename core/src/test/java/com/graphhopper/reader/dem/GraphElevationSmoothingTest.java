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
 * @author Robin Boldt
 */
public class GraphElevationSmoothingTest {

    @Test
    public void interpolatesElevationOfPillarNodes() {

        PointList pl1 = new PointList(3, true);
        pl1.add(0, 0, 0);
        pl1.add(0.0005, 0.0005, 100);
        pl1.add(0.001, 0.001, 50);
        GraphElevationSmoothing.smoothElevation(pl1);
        assertEquals(3, pl1.size());
        assertEquals(50, pl1.getEle(1), .1);

        PointList pl2 = new PointList(3, true);
        pl2.add(0.001, 0.001, 50);
        pl2.add(0.0015, 0.0015, 160);
        pl2.add(0.0016, 0.0015, 150);
        pl2.add(0.0017, 0.0015, 220);
        pl2.add(0.002, 0.002, 20);
        GraphElevationSmoothing.smoothElevation(pl2);
        assertEquals(5, pl2.size());
        assertEquals(120, pl2.getEle(1), .1);
        // This is not 120 anymore, as the point at index 1 was smoothed from 160=>120
        assertEquals(112, pl2.getEle(2), .1);

        assertEquals(50, pl2.getEle(0), .1);
    }

}
