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

/**
 * We need the supercover line. The best algorithm is a 'voxel grid traversal algorithm' and
 * described in "A Fast Voxel Traversal Algorithm for Ray Tracing" by John Amanatides and Andrew Woo
 * (1987): http://www.cse.yorku.ca/~amana/research/grid.pdf
 * <p>
 * Other methods we used are Bresenham (only integer start and end values) and Xiaolin Wu (anti
 * aliasing). See some discussion here: http://stackoverflow.com/a/3234074/194609 and here
 * http://stackoverflow.com/q/24679963/194609
 * <p/>
 * @author Peter Karich
 */
public class BresenhamLine
{
    public static void calcPoints( int y1, int x1, int y2, int x2,
            PointEmitter emitter )
    {
        bresenham(y1, x1, y2, x2, emitter);
    }

    public static void voxelTraversal( double y1, double x1, double y2, double x2,
            PointEmitter emitter )
    {
        // edge case
        x1 = fix(x1);
        y1 = fix(y1);
        x2 = fix(x2);
        y2 = fix(y2);

        int x = (int) x1, y = (int) y1;
        int endX = (int) x2, endY = (int) y2;

        // deltaX and Y is how far we have to move in ray direction until we find a new cell in x or y direction
        // y = u + t * v, where u=(x1,x2) and v=(stepX,stepY) is the direction vector
        final double gridCellWidth = 1, gridCellHeight = 1;

        double deltaX = gridCellWidth / Math.abs(x2 - x1);
        int stepX = (int) Math.signum(x2 - x1);
        double tmp = frac(x1 / gridCellWidth);
        double maxX = deltaX * (1.0 - tmp);

        double deltaY = gridCellHeight / Math.abs(y2 - y1);
        int stepY = (int) Math.signum(y2 - y1);
        tmp = frac(y1 / gridCellHeight);
        double maxY = deltaY * (1.0 - tmp);

        boolean reachedY = false, reachedX = false;

        emitter.set(y, x);
        // trace primary ray
        while (!(reachedX && reachedY))
        {
            if (maxX < maxY)
            {
                maxX += deltaX;
                x += stepX;
            } else
            {
                maxY += deltaY;
                y += stepY;
            }

            emitter.set(y, x);

            if (stepX > 0.0)
            {
                if (x >= endX)
                    reachedX = true;

            } else if (x <= endX)
            {
                reachedX = true;
            }

            if (stepY > 0.0)
            {
                if (y >= endY)
                    reachedY = true;

            } else if (y <= endY)
            {
                reachedY = true;
            }
        }
    }

    static final double fix( double val )
    {
        if (frac(val) == 0)
            return val + 0.1;
        return val;
    }

    static final double frac( double val )
    {
        return val - (int) val;
    }

    public static void bresenham( int y1, int x1, int y2, int x2,
            PointEmitter emitter )
    {
        boolean latIncreasing = y1 < y2;
        boolean lonIncreasing = x1 < x2;
        int dLat = Math.abs(y2 - y1), sLat = latIncreasing ? 1 : -1;
        int dLon = Math.abs(x2 - x1), sLon = lonIncreasing ? 1 : -1;
        int err = dLon - dLat;

        while (true)
        {
            emitter.set(y1, x1);
            if (y1 == y2 && x1 == x2)
                break;

            int tmpErr = 2 * err;
            if (tmpErr > -dLat)
            {
                err -= dLat;
                x1 += sLon;
            }

            if (tmpErr < dLon)
            {
                err += dLon;
                y1 += sLat;
            }
        }
    }

    public static void xiaolinWu( double y1, double x1, double y2, double x2,
            PointEmitter emitter )
    {
        double dx = x2 - x1;
        double dy = y2 - y1;

        if (Math.abs(dx) > Math.abs(dy))
        {
            if (x2 < x1)
            {
                // algo only handles rightwards so swap
                double tmp = x1;
                x1 = x2;
                x2 = tmp;
                tmp = y1;
                y1 = y2;
                y2 = tmp;
            }

            double gradient = dy / dx;
            // orig: round
            int xend = (int) (x1);
            double yend = y1 + gradient * (xend - x1);
            int xpxl1 = xend;
            int ypxl1 = (int) yend;

            // first endpoint
            emitter.set(ypxl1, xpxl1);
            emitter.set(ypxl1 + 1, xpxl1);
            double intery = yend + gradient;

            // orig: round
            xend = (int) (x2);
            yend = y2 + gradient * (xend - x2);
            int xpxl2 = xend;
            int ypxl2 = (int) yend;

            // second endpoint
            emitter.set(ypxl2, xpxl2);
            emitter.set(ypxl2 + 1, xpxl2);

            // all the points between the endpoints
            for (int x = xpxl1 + 1; x <= xpxl2 - 1; ++x)
            {
                emitter.set((int) intery, x);
                emitter.set((int) intery + 1, x);
                intery += gradient;
            }
        } else
        {
            if (y2 < y1)
            {
                // algo only handles topwards so swap
                double tmp = x1;
                x1 = x2;
                x2 = tmp;
                tmp = y1;
                y1 = y2;
                y2 = tmp;
            }

            double gradient = dx / dy;
            // orig: round
            int yend = (int) (y1);
            double xend = x1 + gradient * (yend - y1);
            int ypxl1 = yend;
            int xpxl1 = (int) xend;

            // first endpoint
            emitter.set(ypxl1, xpxl1);
            emitter.set(ypxl1 + 1, xpxl1);
            double interx = xend + gradient;

            // orig: round
            yend = (int) (y2);
            xend = x2 + gradient * (yend - y2);
            int ypxl2 = yend;
            int xpxl2 = (int) xend;

            // second endpoint
            emitter.set(ypxl2, xpxl2);
            emitter.set(ypxl2 + 1, xpxl2);

            // all the points between the endpoints
            for (int y = ypxl1 + 1; y <= ypxl2 - 1; ++y)
            {
                emitter.set(y, (int) interx);
                emitter.set(y, (int) interx + 1);
                interx += gradient;
            }
        }
    }

    public static void calcPoints( final double lat1, final double lon1,
            final double lat2, final double lon2,
            final PointEmitter emitter,
            final double offsetLat, final double offsetLon,
            final double deltaLat, final double deltaLon )
    {
//        double y1 = (lat1 - offsetLat) / deltaLat;
//        double x1 = (lon1 - offsetLon) / deltaLon;
//        double y2 = (lat2 - offsetLat) / deltaLat;
//        double x2 = (lon2 - offsetLon) / deltaLon;
        // for xiaolinWu or calcPoints

        // round to make results of bresenham closer to correct solution
        int y1 = (int) ((lat1 - offsetLat) / deltaLat);
        int x1 = (int) ((lon1 - offsetLon) / deltaLon);
        int y2 = (int) ((lat2 - offsetLat) / deltaLat);
        int x2 = (int) ((lon2 - offsetLon) / deltaLon);
        bresenham(y1, x1, y2, x2, new PointEmitter()
        {
            @Override
            public void set( double lat, double lon )
            {
                // +.1 to move more near the center of the tile
                emitter.set((lat + .1) * deltaLat + offsetLat, (lon + .1) * deltaLon + offsetLon);
            }
        });
    }
}
