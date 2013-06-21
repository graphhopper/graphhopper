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
package com.graphhopper.ui;

import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.Location2IDQuadtree;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * @author Peter Karich
 */
class DebugLocation2IDQuadtree extends Location2IDQuadtree
{
    private GraphicsWrapper mg;
    private Graphics2D g2;

    public DebugLocation2IDQuadtree( Graph g, GraphicsWrapper mg, Directory dir )
    {
        super(g, dir);
        this.mg = mg;
    }

    public void setGraphics( Graphics2D g2 )
    {
        this.g2 = g2;
        double w = getMaxRasterWidthMeter();
        // System.out.println("w:" + w);
        double startLon = mg.getLon(0);
        double lat1 = mg.getLat(0);
        double lat2 = mg.getLat(500);
        g2.setColor(Color.ORANGE);
        int lines = 1000;
        for (int i = 0; i < lines; i++)
        {
            double c1 = distCalc.calcCircumference(lat1);
            double addLon1 = 360 * i * w / c1;
            double c2 = distCalc.calcCircumference(lat1);
            double addLon2 = 360 * i * w / c2;
            int x1 = (int) mg.getX(startLon + addLon1);
            int x2 = (int) mg.getX(startLon + addLon2);
            g2.drawLine(x1, (int) mg.getY(lat1), x2, (int) mg.getY(lat2));
        }
    }

    @Override
    public int findID( double lat, double lon )
    {
        int ret = super.findID(lat, lon);
        mg.plotNode(g2, ret, Color.GREEN);
        return ret;
    }

    @Override
    public void goFurtherHook( int n )
    {
        if (g2 != null)
        {
            mg.plotNode(g2, n, Color.RED);
        }
    }
}
