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

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.RAMDirectory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Karich
 */
public class HeightTileTest {
    @Test
    public void testGetHeight() {
        // data access has same coordinate system as graphical or UI systems have (or the original DEM data has).
        // But HeightTile has lat,lon system ('mathematically')
        int width = 10;
        int height = 20;
        double precision = 1e6;
        HeightTile instance = new HeightTile(0, 0, width, height, precision, 10, 20);
        DataAccess heights = new RAMDirectory().create("tmp");
        heights.create(2 * width * height);
        instance.setHeights(heights);
        init(heights, width, height, 1);

        // x,y=1,7
        heights.setShort(2 * (17 * width + 1), (short) 70);

        // x,y=2,9
        heights.setShort(2 * (19 * width + 2), (short) 90);

        assertEquals(1, instance.getHeight(5, 5), 1e-3);
        assertEquals(70, instance.getHeight(2.5, 1.5), 1e-3);
        // edge cases for one tile with the boundaries [min,min+degree/width) for lat and lon
        assertEquals(1, instance.getHeight(3, 2), 1e-3);
        assertEquals(70, instance.getHeight(2, 1), 1e-3);

        // edge cases for the whole object
        assertEquals(1, instance.getHeight(+1.0, 2), 1e-3);
        assertEquals(90, instance.getHeight(0.5, 2.5), 1e-3);
        assertEquals(90, instance.getHeight(0.0, 2.5), 1e-3);
        assertEquals(1, instance.getHeight(+0.0, 3), 1e-3);

        assertEquals(1, instance.getHeight(0, 0), 1e-3);
        assertEquals(1, instance.getHeight(9, 10), 1e-3);
        assertEquals(1, instance.getHeight(10, 9), 1e-3);
        assertEquals(1, instance.getHeight(10, 10), 1e-3);
    }

    @Test
    public void testGetHeightForNegativeTile() {
        int width = 10;
        HeightTile instance = new HeightTile(-20, -20, width, width, 1e6, 10, 10);
        DataAccess heights = new RAMDirectory().create("tmp");
        heights.create(2 * 10 * 10);
        instance.setHeights(heights);
        init(heights, width, width, 1);

        // x,y=1,7
        heights.setShort(2 * (7 * width + 1), (short) 70);

        // x,y=2,9
        heights.setShort(2 * (9 * width + 2), (short) 90);

        assertEquals(1, instance.getHeight(-15, -15), 1e-3);
        assertEquals(70, instance.getHeight(-17.5, -18.5), 1e-3);
        // edge cases for one tile with the boundaries [min,min+degree/width) for lat and lon
        assertEquals(1, instance.getHeight(-17, -18), 1e-3);
        assertEquals(70, instance.getHeight(-18, -19), 1e-3);
    }

    @Test
    public void testInterpolate() {
        HeightTile instance = new HeightTile(0, 0, 2, 2, 1e6, 10, 10).setInterpolate(true);
        DataAccess heights = new RAMDirectory().create("tmp");
        heights.create(2 * 2 * 2);
        instance.setHeights(heights);
        double topLeft = 0;
        double topRight = 1;
        double bottomLeft = 2;
        double bottomRight = 3;
        set(heights, 2, 0, 0, (short) topLeft);
        set(heights, 2, 1, 0, (short) topRight);
        set(heights, 2, 0, 1, (short) bottomLeft);
        set(heights, 2, 1, 1, (short) bottomRight);

        // corners
        assertEquals(bottomLeft, instance.getHeight(0, 0), 1e-3);
        assertEquals(topLeft, instance.getHeight(10, 0), 1e-3);
        assertEquals(bottomRight, instance.getHeight(0, 10), 1e-3);
        assertEquals(topRight, instance.getHeight(10, 10), 1e-3);

        // midpoints
        assertEquals(avg(topLeft, topRight), instance.getHeight(10, 5), 1e-3);
        assertEquals(avg(bottomLeft, bottomRight), instance.getHeight(0, 5), 1e-3);
        assertEquals(avg(topLeft, bottomLeft), instance.getHeight(5, 0), 1e-3);
        assertEquals(avg(topRight, bottomRight, topLeft, bottomLeft), instance.getHeight(5, 5), 1e-3);

        // missing data uses whatever remains
        set(heights, 2, 1, 0, Short.MIN_VALUE);
        set(heights, 2, 0, 1, Short.MIN_VALUE);
        set(heights, 2, 1, 1, Short.MIN_VALUE);
        assertEquals(topLeft, instance.getHeight(0, 0), 1e-3);
        assertEquals(topLeft, instance.getHeight(10, 0), 1e-3);
        assertEquals(topLeft, instance.getHeight(0, 10), 1e-3);
        assertEquals(topLeft, instance.getHeight(10, 10), 1e-3);

        // when all data missing, returns NaN
        set(heights, 2, 0, 0, Short.MIN_VALUE);
        assertEquals(Double.NaN, instance.getHeight(5, 5), 1e-3);
    }

    private void init(DataAccess da, int width, int height, int i) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                set(da, width, x, y, (short) 1);
            }
        }
    }

    private void set(DataAccess da, int width, int x, int y, short height) {
        da.setShort(2 * (y * width + x), height);
    }

    private double avg(double... ns) {
        double sum = 0;
        for (double n : ns) {
            sum += n;
        }
        return sum / ns.length;
    }
}