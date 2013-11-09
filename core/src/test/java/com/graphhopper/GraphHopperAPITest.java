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
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.GraphBuilder;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class GraphHopperAPITest
{
    final EncodingManager encodingManager = new EncodingManager("CAR");

    @Test
    public void testLoad()
    {
        GraphStorage graph = new GraphBuilder(encodingManager).create();
        graph.setNode(0, 42, 10);
        graph.setNode(1, 42.1, 10.1);
        graph.setNode(2, 42.1, 10.2);
        graph.setNode(3, 42, 10.4);
        graph.setNode(4, 41.9, 10.2);

        graph.edge(0, 1, 10, true);
        graph.edge(1, 2, 10, false);
        graph.edge(2, 3, 10, true);
        graph.edge(0, 4, 40, true);
        graph.edge(4, 3, 40, true);

        GraphHopperAPI instance = new GraphHopper().setEncodingManager(encodingManager).disableCHShortcuts().loadGraph(graph);
        GHResponse ph = instance.route(new GHRequest(42, 10.4, 42, 10));
        assertTrue(ph.isFound());
        assertEquals(80, ph.getDistance(), 1e-6);
        assertEquals(42, ph.getPoints().getLatitude(0), 1e-5);
        assertEquals(10.4, ph.getPoints().getLongitude(0), 1e-5);
        assertEquals(41.9, ph.getPoints().getLatitude(1), 1e-5);
        assertEquals(10.2, ph.getPoints().getLongitude(1), 1e-5);
        assertEquals(3, ph.getPoints().getSize());
    }

    @Test
    public void testNoLoad()
    {

        GraphHopperAPI instance = new GraphHopper().setEncodingManager(encodingManager).disableCHShortcuts();
        try
        {
            instance.route(new GHRequest(42, 10.4, 42, 10));
            assertTrue(false);
        } catch (Exception ex)
        {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Call load or importOrLoad before routing"));
        }

        instance = new GraphHopper().setEncodingManager(encodingManager);
        try
        {
            instance.route(new GHRequest(42, 10.4, 42, 10));
            assertTrue(false);
        } catch (Exception ex)
        {
            assertTrue(ex.getMessage(), ex.getMessage().startsWith("Call load or importOrLoad before routing"));
        }

    }
}
