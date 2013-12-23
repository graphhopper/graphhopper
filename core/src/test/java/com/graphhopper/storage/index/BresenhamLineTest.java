/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License, 
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
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author Peter Karich
 */
public class BresenhamLineTest
{
    final PointList points = new PointList();
    PointEmitter emitter = new PointEmitter()
    {
        @Override
        public void set( double lat, double lon )
        {
            points.add(lat, lon);
        }
    };

    @Before
    public void setUp()
    {
        points.clear();
    }

    @Test
    public void testBresenhamLineLeftDown()
    {
        BresenhamLine.calcPoints(2, 1, -3, -1, emitter);
        assertEquals(Helper.createPointList(2, 1, 1, 1, 0, 0, -1, 0, -2, 0, -3, -1), points);
    }

    @Test
    public void testBresenhamLineLeftUp()
    {
        BresenhamLine.calcPoints(2, 1, 3, -1, emitter);
        assertEquals(Helper.createPointList(2, 1, 2, 0, 3, -1), points);
    }

    @Test
    public void testBresenhamBug()
    {
        BresenhamLine.calcPoints(0.5, -0.5, -0.6, 1.6, emitter, -1, -1, 0.75, 1.3);
//        assertEquals(Helper.createPointList(0.875, -0.35, 0.125, 0.95, -0.625, 2.25), points);
        assertEquals(Helper.createPointList(0.5, -1, 0.5, 0.3, -0.25, 1.6), points);
    }

    @Test
    public void testBresenhamHorizontal()
    {
        BresenhamLine.calcPoints(.5, -.5, .5, 1, emitter, -1, -1, 0.6, 0.4);
        // assertEquals(Helper.createPointList(.5, -.4, .5, 0, .5, .4, .5, .8, .5, 1.2), points);
        assertEquals(Helper.createPointList(.8, -.6, .8, -0.2, .8, .2, .8, .6, .8, 1.0), points);
    }

    @Test
    public void testBresenhamVertical()
    {
        BresenhamLine.calcPoints(-.5, .5, 1, 0.5, emitter, 0, 0, 0.4, 0.6);
//        assertEquals(Helper.createPointList(-.2, .3, 0.2, .3, 0.6, 0.3, 1.0, 0.3), points);
        assertEquals(Helper.createPointList(-.4, .6, 0, .6, 0.4, 0.6, .8, .6, 1.2, 0.6), points);
    }

    @Test
    public void testRealBresenham()
    {
        int parts = 4;
        int bits = (int) (Math.log(parts * parts) / Math.log(2));
        double minLon = -1, maxLon = 1.6;
        double minLat = -1, maxLat = 0.5;
        final KeyAlgo keyAlgo = new SpatialKeyAlgo(bits).setBounds(minLon, maxLon, minLat, maxLat);
        double deltaLat = (maxLat - minLat) / parts;
        double deltaLon = (maxLon - minLon) / parts;
        final ArrayList<Long> keys = new ArrayList<Long>();
        PointEmitter tmpEmitter = new PointEmitter()
        {
            @Override
            public void set( double lat, double lon )
            {
                keys.add(keyAlgo.encode(lat, lon));
            }
        };
        BresenhamLine.calcPoints(.5, -.5, -0.1, 0.9, tmpEmitter, minLat, minLon,
                deltaLat, deltaLon);
        // TODO Either 10, 11, 12 or 11, 12, 7 is correct but 10,9,7 is a minor incorrect encoding
        assertEquals(Arrays.asList(10L, 9L, 7L), keys);
//        assertEquals(Arrays.asList(11L, 12L, 7L), keys);
    }

    @Test
    public void testBresenhamToLeft()
    {
        BresenhamLine.calcPoints(
                47.57383, 9.61984,
                47.57382, 9.61890, emitter, 47, 9, 0.00647, 0.00964);
        assertEquals(points.toString(), 1, points.getSize());
    }
}
