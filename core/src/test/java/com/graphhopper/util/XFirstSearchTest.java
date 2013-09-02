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
package com.graphhopper.util;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.Graph;
import gnu.trove.set.hash.TIntHashSet;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class XFirstSearchTest
{
    int counter;
    TIntHashSet set = new TIntHashSet();

    @Before
    public void setup()
    {
        counter = 0;
    }

    @Test
    public void testBFS()
    {
        XFirstSearch bfs = new XFirstSearch()
        {
            @Override
            public boolean goFurther( int v )
            {
                counter++;
                assertTrue("v " + v + " is already contained in set. iteration:" + counter, !set.contains(v));
                set.add(v);
                return super.goFurther(v);
            }
        };

        Graph g = new GraphBuilder(new EncodingManager("CAR")).create();
        g.edge(0, 1, 85, true);
        g.edge(0, 2, 217, true);
        g.edge(0, 3, 173, true);
        g.edge(0, 5, 173, true);
        g.edge(1, 6, 75, true);
        g.edge(2, 7, 51, true);
        g.edge(3, 8, 23, true);
        g.edge(4, 8, 793, true);
        g.edge(8, 10, 343, true);
        g.edge(6, 9, 72, true);
        g.edge(9, 10, 8, true);
        g.edge(5, 10, 1, true);

        bfs.start(g.createEdgeExplorer(), 0, false);

        assertTrue(counter > 0);
        assertEquals(g.getNodes(), counter);
    }
}
