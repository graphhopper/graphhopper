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
package com.graphhopper;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.*;
import com.graphhopper.util.PointList;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperAPITest
{
    final EncodingManager encodingManager = new EncodingManager("car");

    @Test
    public void testLoad()
    {
        GraphHopperStorage graph = new GraphBuilder(encodingManager).create();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 42, 10);
        na.setNode(1, 42.1, 10.1);
        na.setNode(2, 42.1, 10.2);
        na.setNode(3, 42, 10.4);
        na.setNode(4, 41.9, 10.2);

        graph.edge(0, 1, 10, true);
        graph.edge(1, 2, 10, false);
        graph.edge(2, 3, 10, true);
        graph.edge(0, 4, 40, true);
        graph.edge(4, 3, 40, true);

        GraphHopper instance = new GraphHopper().
                setStoreOnFlush(false).
                setEncodingManager(encodingManager).setCHEnabled(false).
                loadGraph(graph);
        GHResponse rsp = instance.route(new GHRequest(42, 10.4, 42, 10));
        assertFalse(rsp.hasErrors());
        PathWrapper arsp = rsp.getBest();
        assertEquals(80, arsp.getDistance(), 1e-6);
        
        PointList points = arsp.getPoints();
        assertEquals(42, points.getLatitude(0), 1e-5);
        assertEquals(10.4, points.getLongitude(0), 1e-5);
        assertEquals(41.9, points.getLatitude(1), 1e-5);
        assertEquals(10.2, points.getLongitude(1), 1e-5);
        assertEquals(3, points.getSize());
        instance.close();
    }

    @Test
    public void testDisconnected179()
    {
        GraphHopperStorage graph = new GraphBuilder(encodingManager).create();
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 42, 10);
        na.setNode(1, 42.1, 10.1);
        na.setNode(2, 42.1, 10.2);
        na.setNode(3, 42, 10.4);

        graph.edge(0, 1, 10, true);
        graph.edge(2, 3, 10, true);

        GraphHopper instance = new GraphHopper().
                setStoreOnFlush(false).
                setEncodingManager(encodingManager).setCHEnabled(false).
                loadGraph(graph);
        GHResponse rsp = instance.route(new GHRequest(42, 10, 42, 10.4));
        assertTrue(rsp.hasErrors());

        try
        {
            rsp.getBest().getPoints();
            assertTrue(false);
        } catch (Exception ex)
        {
        }

        instance.close();
    }

    @Test
    public void testNoLoad()
    {
        GraphHopper instance = new GraphHopper().
                setStoreOnFlush(false).
                setEncodingManager(encodingManager).setCHEnabled(false);
        try
        {
            instance.route(new GHRequest(42, 10.4, 42, 10));
            assertTrue(false);
        } catch (Exception ex)
        {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Do a successful call to load or importOrLoad before routing"));
        }

        instance = new GraphHopper().setEncodingManager(encodingManager);
        try
        {
            instance.route(new GHRequest(42, 10.4, 42, 10));
            assertTrue(false);
        } catch (Exception ex)
        {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Do a successful call to load or importOrLoad before routing"));
        }
    }
}
