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
package com.graphhopper.ui;

import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;

/**
 * @author Peter Karich
 */
public class GraphicsWrapper {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final NodeAccess na;
    private double scaleX;
    private double scaleY;
    private double offsetX;
    private double offsetY;
    private BBox bounds = new BBox(-180, 180, -90, 90);

    public GraphicsWrapper(Graph g) {
        this.na = g.getNodeAccess();
        BBox b = g.getBounds();
        scaleX = scaleY = 0.002 * (b.maxLat - b.minLat);
        offsetY = b.maxLat - 90;
        offsetX = -b.minLon;
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public double getScaleX() {
        return scaleX;
    }

    public double getScaleY() {
        return scaleY;
    }

    public void plotText(Graphics2D g2, double lat, double lon, String text) {
        g2.drawString(text, (int) getX(lon) + 5, (int) getY(lat) + 5);
    }

    public void plotDirectedEdge(Graphics2D g2, double lat, double lon, double lat2, double lon2, float width) {

        g2.setStroke(new BasicStroke(width));
        int startLon = (int) getX(lon);
        int startLat = (int) getY(lat);
        int destLon = (int) getX(lon2);
        int destLat = (int) getY(lat2);

        g2.drawLine(startLon, startLat, destLon, destLat);

        // only for deep zoom show direction
        if (scaleX < 0.0001) {
            g2.setStroke(new BasicStroke(3));
            Path2D.Float path = new Path2D.Float();
            path.moveTo(destLon, destLat);
            path.lineTo(destLon + 6, destLat - 2);
            path.lineTo(destLon + 6, destLat + 2);
            path.lineTo(destLon, destLat);

            AffineTransform at = new AffineTransform();
            double angle = Math.atan2(lat2 - lat, lon2 - lon);
            at.rotate(-angle + Math.PI, destLon, destLat);
            path.transform(at);
            g2.draw(path);
        }
    }

    public void plotEdge(Graphics2D g2, double lat, double lon, double lat2, double lon2, float width) {
        g2.setStroke(new BasicStroke(width));
        g2.drawLine((int) getX(lon), (int) getY(lat), (int) getX(lon2), (int) getY(lat2));
    }

    public void plotEdge(Graphics2D g2, double lat, double lon, double lat2, double lon2) {
        plotEdge(g2, lat, lon, lat2, lon2, 1);
    }

    public double getX(double lon) {
        return (lon + offsetX) / scaleX;
    }

    public double getY(double lat) {
        return (90 - lat + offsetY) / scaleY;
    }

    public double getLon(int x) {
        return x * scaleX - offsetX;
    }

    public double getLat(int y) {
        return 90 - (y * scaleY - offsetY);
    }

    public void plotNode(Graphics2D g2, int loc, Color c) {
        plotNode(g2, loc, c, 4);
    }


    public void plotNode(Graphics2D g2, int loc, Color c, int size) {
        plotNode(g2, loc, c, size, "");
    }

    public void plotNode(Graphics2D g2, int loc, Color c, int size, String text) {
        plotNode(g2, na, loc, c, size, "");
    }

    public void plotNode(Graphics2D g2, NodeAccess na, int loc, Color c, int size, String text) {
        double lat = na.getLatitude(loc);
        double lon = na.getLongitude(loc);
        if (lat < bounds.minLat || lat > bounds.maxLat || lon < bounds.minLon || lon > bounds.maxLon) {
            return;
        }

        Color old = g2.getColor();
        g2.setColor(c);
        plot(g2, lat, lon, size);
        g2.setColor(old);
    }

    public void plot(Graphics2D g2, double lat, double lon, int width) {
        double x = getX(lon);
        double y = getY(lat);
        g2.fillOval((int) x, (int) y, width, width);
    }

    public void scale(int x, int y, boolean zoomIn) {
        double tmpFactor = 0.5f;
        if (!zoomIn) {
            tmpFactor = 2;
        }

        double oldScaleX = scaleX;
        double oldScaleY = scaleY;
        double resX = scaleX * tmpFactor;
        if (resX > 0) {
            scaleX = resX;
        }

        double resY = scaleY * tmpFactor;
        if (resY > 0) {
            scaleY = resY;
        }

        // respect mouse x,y when scaling
        // TODO minor bug: compute difference of lat,lon position for mouse before and after scaling
        if (zoomIn) {
            offsetX -= (offsetX + x) * scaleX;
            offsetY -= (offsetY + y) * scaleY;
        } else {
            offsetX += x * oldScaleX;
            offsetY += y * oldScaleY;
        }

        logger.info("mouse wheel moved => repaint. zoomIn:" + zoomIn + " " + offsetX + "," + offsetY
                + " " + scaleX + "," + scaleY);
    }

    public void setNewOffset(int offX, int offY) {
        offsetX += offX * scaleX;
        offsetY += offY * scaleY;
    }

    public BBox setBounds(int minX, int maxX, int minY, int maxY) {
        double minLon = getLon(minX);
        double maxLon = getLon(maxX);

        double maxLat = getLat(minY);
        double minLat = getLat(maxY);
        bounds = new BBox(minLon, maxLon, minLat, maxLat);
        return bounds;
    }
}
