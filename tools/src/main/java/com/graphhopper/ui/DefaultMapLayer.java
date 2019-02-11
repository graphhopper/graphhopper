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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;

/**
 * @author Peter Karich
 */
public abstract class DefaultMapLayer implements MapLayer {
    protected BufferedImage image;
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Rectangle bounds = new Rectangle();
    private Graphics2D tmpG;
    private boolean buffering = true;
    // a bit transparent:
//    private RescaleOp op = new RescaleOp(new float[]{1f, 1f, 1f, 0.5f}, new float[4], null);
    private RescaleOp op = new RescaleOp(new float[]{1f, 1f, 1f, 1f}, new float[4], null);

    protected abstract void paintComponent(Graphics2D createGraphics);

    @Override
    public void paint(Graphics2D mainGraphics) {
        if (!buffering) {
            try {
                paintComponent(mainGraphics);
            } catch (Exception ex) {
                logger.error("Problem in paintComponent", ex);
            }
            return;
        }
        if (image != null) {
            mainGraphics.drawImage(image, op, bounds.x, bounds.y);
        }
    }

    @Override
    public void setBuffering(boolean enable) {
        buffering = enable;
    }

    @Override
    public final void repaint() {
        if (tmpG != null) {
            paintComponent(tmpG);
        }
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }

    @Override
    public void setBounds(Rectangle bounds) {
        if (image == null || image.getHeight() != bounds.height || image.getWidth() != bounds.width) {
            image = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
            tmpG = image.createGraphics();
            tmpG.setColor(Color.BLACK);
            tmpG.setBackground(Color.WHITE);
        }
        this.bounds = bounds;
        repaint();
    }

    public void makeTransparent(Graphics2D g2) {
        Color col = g2.getColor();
        Composite comp = null;
        // force transparence of this layer only. If no buffering we would clear layers below
        if (buffering) {
            comp = g2.getComposite();
            g2.setComposite(AlphaComposite.Clear);
        }
        g2.setColor(new Color(0, 0, 0, 0));
        g2.fillRect(0, 0, bounds.width, bounds.height);
        g2.setColor(col);
        if (comp != null) {
            g2.setComposite(comp);
        }
    }

    public void clearGraphics(Graphics2D g2) {
        g2.clearRect(0, 0, bounds.width, bounds.height);
    }
}
