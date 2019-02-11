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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One rectangle of height data from Shuttle Radar Topography Mission.
 * <p>
 *
 * @author Peter Karich
 */
public class HeightTile {
    private final int minLat;
    private final int minLon;
    private final int width;
    private final int height;
    private final int horizontalDegree;
    private final int verticalDegree;
    private final double lowerBound;
    private final double lonHigherBound;
    private final double latHigherBound;
    private DataAccess heights;
    private boolean calcMean;

    public HeightTile(int minLat, int minLon, int width, int height, double precision, int horizontalDegree, int verticalDegree) {
        this.minLat = minLat;
        this.minLon = minLon;
        this.width = width;
        this.height = height;

        this.lowerBound = -1 / precision;
        this.lonHigherBound = horizontalDegree + 1 / precision;
        this.latHigherBound = verticalDegree + 1 / precision;

        this.horizontalDegree = horizontalDegree;
        this.verticalDegree = verticalDegree;
    }

    public HeightTile setCalcMean(boolean b) {
        this.calcMean = b;
        return this;
    }

    public boolean isSeaLevel() {
        return heights.getHeader(0) == 1;
    }

    public HeightTile setSeaLevel(boolean b) {
        heights.setHeader(0, b ? 1 : 0);
        return this;
    }

    void setHeights(DataAccess da) {
        this.heights = da;
    }

    public double getHeight(double lat, double lon) {
        double deltaLat = Math.abs(lat - minLat);
        double deltaLon = Math.abs(lon - minLon);
        if (deltaLat > latHigherBound || deltaLat < lowerBound)
            throw new IllegalStateException("latitude not in boundary of this file:" + lat + "," + lon + ", this:" + this.toString());
        if (deltaLon > lonHigherBound || deltaLon < lowerBound)
            throw new IllegalStateException("longitude not in boundary of this file:" + lat + "," + lon + ", this:" + this.toString());

        // first row in the file is the northernmost one
        // http://gis.stackexchange.com/a/43756/9006
        int lonSimilar = (int) (width / horizontalDegree * deltaLon);
        // different fallback methods for lat and lon as we have different rounding (lon -> positive, lat -> negative)
        if (lonSimilar >= width)
            lonSimilar = width - 1;
        int latSimilar = height - 1 - (int) (height / verticalDegree * deltaLat);
        if (latSimilar < 0)
            latSimilar = 0;

        // always keep in mind factor 2 because of short value
        int daPointer = 2 * (latSimilar * width + lonSimilar);
        int value = heights.getShort(daPointer);
        AtomicInteger counter = new AtomicInteger(1);
        if (value == Short.MIN_VALUE)
            return Double.NaN;

        if (calcMean) {
            if (lonSimilar > 0)
                value += includePoint(daPointer - 2, counter);

            if (lonSimilar < width - 1)
                value += includePoint(daPointer + 2, counter);

            if (latSimilar > 0)
                value += includePoint(daPointer - 2 * width, counter);

            if (latSimilar < height - 1)
                value += includePoint(daPointer + 2 * width, counter);
        }

        return (double) value / counter.get();
    }

    private double includePoint(int pointer, AtomicInteger counter) {
        short value = heights.getShort(pointer);
        if (value == Short.MIN_VALUE)
            return 0;

        counter.incrementAndGet();
        return value;
    }

    public void toImage(String imageFile) throws IOException {
        ImageIO.write(makeARGB(), "PNG", new File(imageFile));
    }

    protected BufferedImage makeARGB() {
        BufferedImage argbImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics g = argbImage.getGraphics();
        long len = width * height;
        for (int i = 0; i < len; i++) {
            int lonSimilar = i % width;
            // no need for width - y as coordinate system for Graphics is already this way
            int latSimilar = i / height;
            int green = Math.abs(heights.getShort(i * 2));
            if (green == 0) {
                g.setColor(new Color(255, 0, 0, 255));
            } else {
                int red = 0;
                while (green > 255) {
                    green = green / 10;
                    red += 50;
                }
                if (red > 255)
                    red = 255;
                g.setColor(new Color(red, green, 122, 255));
            }
            g.drawLine(lonSimilar, latSimilar, lonSimilar, latSimilar);
        }
        g.dispose();
        return argbImage;
    }

    public BufferedImage getImageFromArray(int[] pixels, int width, int height) {
        BufferedImage tmpImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        tmpImage.setRGB(0, 0, width, height, pixels, 0, width);
        return tmpImage;
    }

    @Override
    public String toString() {
        return minLat + "," + minLon;
    }
}