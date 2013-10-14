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

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.CoordTrig;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class LocationIDResultTest
{
    @Test
    public void testCalcSnappedPoint()
    {
        EncodingManager encodingManager = new EncodingManager("CAR");
        Graph g = new GraphStorage(new RAMDirectory(), encodingManager).create(100);
        g.setNode(0, 1, 0);
        g.setNode(1, 1, 2.5);
        g.setNode(2, 0, 0);
        g.edge(0, 2, 10, true);
        g.edge(0, 1, 10, true).setWayGeometry(Helper.createPointList(1.5, 1, 1.5, 1.5));

        DistanceCalc distC = new DistanceCalc();
        EdgeExplorer expl = g.createEdgeExplorer();

        // snap directly to tower node => pointList could get of size 1?!?      
        // a)
        expl.setBaseNode(2).next();
        LocationIDResult match = createLocationResult(1, -1, expl, 0);        
        match.calcSnappedPoint(distC);
        PointList basePoints = match.getBaseEdge().getWayGeometry(3);
        PointList adjPoints = match.getAdjEdge().getWayGeometry(3);        
        assertEquals(new CoordTrig(0, 0), match.getSnappedPoint());        
        assertEquals(1, basePoints.getSize());
        assertEquals(0, basePoints.getLatitude(0), 1e-7);        
        assertEquals(2, adjPoints.getSize());
        assertEquals(0, adjPoints.getLatitude(0), 1e-7);
        assertEquals(1, adjPoints.getLatitude(1), 1e-7);
        // b)
        expl.setBaseNode(1).next();
        match = createLocationResult(1.2, 2.7, expl, 0);
        match.calcSnappedPoint(distC);
        assertEquals(new CoordTrig(1, 2.5), match.getSnappedPoint());
        assertEquals(1, match.getBaseEdge().getWayGeometry(3).getSize());
        assertEquals(4, match.getAdjEdge().getWayGeometry(3).getSize());
        
        // snap directly to pillar node
        expl.setBaseNode(1).next();
        match = createLocationResult(2, 1.5, expl, 1);
        match.calcSnappedPoint(distC);
        assertEquals(2, match.getBaseEdge().getWayGeometry(3).getSize());
        assertEquals(3, match.getAdjEdge().getWayGeometry(3).getSize());
        assertEquals(new CoordTrig(1.5, 1.5), match.getSnappedPoint());
        match = createLocationResult(2, 1.7, expl, 1);
        match.calcSnappedPoint(distC);
        assertEquals(new CoordTrig(1.5, 1.5), match.getSnappedPoint());
        assertEquals(2, match.getBaseEdge().getWayGeometry(3).getSize());
        assertEquals(3, match.getAdjEdge().getWayGeometry(3).getSize());
        
        // snap to edge - with pillar nodes        
        match = createLocationResult(1.5, 2, expl, 0);
        match.calcSnappedPoint(distC);
        assertEquals(new CoordTrig(1.3, 1.9), match.getSnappedPoint());
        assertEquals(2, match.getBaseEdge().getWayGeometry(3).getSize());
        assertEquals(4, match.getAdjEdge().getWayGeometry(3).getSize());

        // snap to edge - without pillar nodes
        expl.setBaseNode(2).next();
        match = createLocationResult(0.5, 0.1, expl, 0);
        match.calcSnappedPoint(distC);
        assertEquals(new CoordTrig(0.5, 0), match.getSnappedPoint());
        assertEquals(2, match.getBaseEdge().getWayGeometry(3).getSize());
        assertEquals(2, match.getAdjEdge().getWayGeometry(3).getSize());
    }

    public LocationIDResult createLocationResult( double lat, double lon, EdgeIteratorState iter, int index )
    {
        LocationIDResult match = new LocationIDResult(lat, lon);
        match.setClosestEdge(iter);
        match.setWayIndex(index);
        return match;
    }
}
