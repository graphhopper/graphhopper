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
package com.graphhopper.reader;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import static com.graphhopper.util.GHUtility.*;
import com.graphhopper.storage.GraphBuilder;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class PrinctonReaderTest
{
    private EncodingManager encodingManager = new EncodingManager("CAR");
    private EdgeFilter carOutEdges = new DefaultEdgeFilter(new EncodingManager("CAR").getEncoder("CAR"), false, true);

    @Test
    public void testRead()
    {
        Graph graph = new GraphBuilder(encodingManager).create();
        new PrinctonReader(graph).stream(PrinctonReader.class.getResourceAsStream("tinyEWD.txt")).read();
        assertEquals(8, graph.nodes());
        assertEquals(2, count(graph.getEdges(0, carOutEdges)));
        assertEquals(3, count(graph.getEdges(6, carOutEdges)));
    }

    @Test
    public void testMediumRead() throws IOException
    {
        Graph graph = new GraphBuilder(encodingManager).create();
        new PrinctonReader(graph).stream(new GZIPInputStream(PrinctonReader.class.getResourceAsStream("mediumEWD.txt.gz"))).read();
        assertEquals(250, graph.nodes());
        assertEquals(13, count(graph.getEdges(244, carOutEdges)));
        assertEquals(11, count(graph.getEdges(16, carOutEdges)));
    }
}
