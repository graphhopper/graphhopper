/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;

/**
 * @author Peter Karich
 */
public abstract class DefaultMapLayer implements MapLayer {

    private Rectangle bounds = new Rectangle();
    private Graphics2D tmpG;
    protected BufferedImage image;
    private boolean buffering = true;
    // a bit transparent:
//    private RescaleOp op = new RescaleOp(new float[]{1f, 1f, 1f, 0.5f}, new float[4], null);
    private RescaleOp op = new RescaleOp(new float[]{1f, 1f, 1f, 1f}, new float[4], null);

    protected abstract void paintComponent(Graphics2D createGraphics);

    @Override public void paint(Graphics2D mainGraphics) {
        if (!buffering) {
            paintComponent(mainGraphics);
            return;
        }
        if (image != null)
            mainGraphics.drawImage(image, op, bounds.x, bounds.y);
    }

    @Override public void setBuffering(boolean enable) {
        buffering = enable;
    }

    @Override public final void repaint() {
        if (tmpG != null)
            paintComponent(tmpG);
    }

    @Override public Rectangle getBounds() {
        return bounds;
    }

    @Override public void setBounds(Rectangle bounds) {
        if (image == null || image.getHeight() != bounds.height || image.getWidth() != bounds.getWidth()) {
            image = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
            tmpG = image.createGraphics();
            tmpG.setColor(Color.BLACK);
            tmpG.setBackground(Color.WHITE);
        }
        this.bounds = bounds;
        repaint();
    }
}
