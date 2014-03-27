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
package com.graphhopper.reader.dem;

import com.graphhopper.storage.DataAccess;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * One rectangle of height data from Shuttle Radar Topography Mission.
 * <p>
 * @author Peter Karich
 */
public class HeightTile
{
    private DataAccess heights;
    private final int minLat;
    private final int minLon;
    private final int width;
    private final double lowerBound;
    private final double higherBound;

    public HeightTile( int minLat, int minLon, int width, double precision )
    {
        this.minLat = minLat;
        this.minLon = minLon;
        this.width = width;

        this.lowerBound = -1 / precision;
        this.higherBound = 1 + 1 / precision;
    }

    void setHeights( DataAccess da )
    {
        this.heights = da;
    }

    public short getHeight( double lat, double lon )
    {
        double deltaLat = lat - minLat;
        double deltaLon = lon - minLon;        
        if (deltaLat > higherBound || deltaLat < lowerBound)
            throw new IllegalStateException("latitude not in boundary of this file:" + lat + "," + lon + ", this:" + this.toString());
        if (deltaLon > higherBound || deltaLon < lowerBound)
            throw new IllegalStateException("longitude not in boundary of this file:" + lat + "," + lon + ", this:" + this.toString());

        // first row in the file is the northernmost one
        // http://gis.stackexchange.com/a/43756/9006
        int lonSimilar = (int) Math.round(width * deltaLon);
        int latSimilar = width - (int) Math.round(width * deltaLat);
        return heights.getShort(2 * (latSimilar * width + lonSimilar));
    }

    public void toImage( String imageFile ) throws IOException
    {
        ImageIO.write(makeARGB(), "PNG", new File(imageFile));
    }

    protected BufferedImage makeARGB()
    {
        int height = width;
        BufferedImage argbImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics g = argbImage.getGraphics();
        long len = heights.getCapacity() / 2;
        for (int i = 0; i < len; i++)
        {
            int lonSimilar = i % width;
            // no need for width - x as coordinate system for Graphics is already this way
            int latSimilar = i / width;
            int green = Math.abs(heights.getShort(i * 2));
            int red = 0;
            while (green > 255)
            {
                green = green / 10;
                red += 50;
            }
            if (red > 255)
                red = 255;
            g.setColor(new Color(red, green, 122, 255));
            g.drawLine(lonSimilar, latSimilar, lonSimilar, latSimilar);
        }
        g.dispose();
        return argbImage;
    }

    public BufferedImage getImageFromArray( int[] pixels, int width )
    {
        int height = width;
        BufferedImage tmpImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        tmpImage.setRGB(0, 0, width, height, pixels, 0, width);
        return tmpImage;
    }

    @Override
    public String toString()
    {
        return minLat + "," + minLon;
    }
}
