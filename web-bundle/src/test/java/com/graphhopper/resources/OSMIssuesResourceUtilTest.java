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

package com.graphhopper.resources;

import com.graphhopper.util.PointList;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMIssuesResourceUtilTest {

    @Test
    public void halfwayOfStraightLineIsNotAnEnd() {
        PointList pl = new PointList();
        pl.add(56.9462739, 24.1182046);
        pl.add(56.9456549, 24.1189004);
        Coordinate at = OSMIssuesResource.halfway(pl);
        assertEquals((56.9462739 + 56.9456549) / 2, at.y, 1e-7);
        assertEquals((24.1182046 + 24.1189004) / 2, at.x, 1e-7);
    }

    @Test
    public void halfwayFollowsLengthNotPointCount() {
        // three points close together at the start, then one long segment: the middle entry is
        // near the start, the middle by length is out on the long segment
        PointList pl = new PointList();
        pl.add(50, 10);
        pl.add(50, 10.0001);
        pl.add(50, 10.0002);
        pl.add(50, 10.0102);
        Coordinate at = OSMIssuesResource.halfway(pl);
        assertEquals(50, at.y, 1e-6);
        assertEquals(10.0051, at.x, 1e-6);
    }

    @Test
    public void halfwayOfZeroLength() {
        PointList pl = new PointList();
        pl.add(50, 10);
        pl.add(50, 10);
        Coordinate at = OSMIssuesResource.halfway(pl);
        assertEquals(50, at.y, 1e-9);
        assertEquals(10, at.x, 1e-9);
    }
}
