/*
 * Copyright 2013 Thomas Buerli <tbuerli@student.ethz.ch>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.storage.index;

import com.graphhopper.routing.util.PublicTransitFlagEncoder;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.Helper;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * 
 * @author Thomas Buerli <tbuerli@student.ethz.ch>
 */
public abstract class AbstractLocationTime2IDIndexTest {

    String location = "./target/tmp/";

    public abstract LocationTime2IDIndex createIndex(Graph g, int resolution);

    @Before
    public void setUp() {
        Helper.removeDir(new File(location));
    }

    @After
    public void tearDown() {
        Helper.removeDir(new File(location));
    }

    Graph createGraph() {
        return createGraph(new RAMDirectory());
    }

    Graph createGraph(Directory dir) {
        return new GraphStorage(dir).create(100);
    }

    /**
     * Test of findID method, of class LocationTime2IDIndex.
     */
    @Test
    public void testFindID() {
        Graph graph = createSimpleGraph();
        LocationTime2IDIndex index = createIndex(graph, 8);
        assertEquals(0, index.findID(0, 1.0001f));
        assertEquals(7, index.findID(1, 2));
        assertEquals(15, index.findID(0.5f, 4.5f));

        assertEquals(0, index.findID(0, 1.0001f, 0));
        assertEquals(2, index.findID(0, 1.0001f, 60));
        assertEquals(2, index.findID(0, 1.0001f, 120));
        assertEquals(4, index.findID(0, 1.0001f,3720));
        assertEquals(4, index.findID(0, 1.0001f,10000));
        
        assertEquals(7, index.findID(1, 2, 0));
        assertEquals(9, index.findID(1, 2, 60));
        assertEquals(9, index.findID(1, 2, 1560));
        assertEquals(13, index.findID(1, 2, 1570));
        assertEquals(13, index.findID(1, 2, 3720));
        assertEquals(13, index.findID(1, 2, 10000));
        
        assertEquals(15, index.findID(0.5f, 4.5f, 0));
        assertEquals(17, index.findID(0.5f, 4.5f, 60));
        assertEquals(17, index.findID(0.5f, 4.5f, 2480));
        assertEquals(20, index.findID(0.5f, 4.5f, 2481));
        assertEquals(20, index.findID(0.5f, 4.5f, 10000));
        
    }

    /**
     * Test of findExitNode method, of class LocationTime2IDIndex.
     */
    @Test
    public void testFindExitNode() {
        Graph graph = createSimpleGraph();
        LocationTime2IDIndex index = createIndex(graph, 8);
        assertEquals(1, index.findExitNode(0, 1.0001f));
        assertEquals(8, index.findExitNode(1, 2));
        assertEquals(16, index.findExitNode(0.5f, 4.5f));
    }

    /**
     * Test of getTime method, of class LocationTime2IDIndex.
     */
    @Test
    public void testGetTime() {
        Graph graph = createSimpleGraph();
        LocationTime2IDIndex index = createIndex(graph, 8);
        assertEquals(0, index.getTime(0));
        assertEquals(0, index.getTime(7));
        assertEquals(0, index.getTime(15));
        
        assertEquals(120, index.getTime(2));
        assertEquals(3720, index.getTime(4));
        
        assertEquals(1560, index.getTime(9));
        assertEquals(3720, index.getTime(13));
        
        assertEquals(2480, index.getTime(17));
        assertEquals(3000, index.getTime(20));
    }

    public Graph createSimpleGraph() {
        Graph graph = createGraph();
        PublicTransitFlagEncoder encoder = new PublicTransitFlagEncoder();
        int alightTime = 240;
        
        int entry = encoder.getEntryFlags();
        int exit = encoder.getExitFlags();
        int transit = encoder.getTransitFlags(false);
        int boarding = encoder.getBoardingFlags(false);
        int alight = encoder.getAlightFlags(false);
        int travel = encoder.flags(false);
        
        // length does not matter here but lat,lon and outgoing edges do!

        //        
        //   lat            
        //    1        /--b--                     
        //            |    \--------c
        //    0       a                  
        //        
        //   lon: 0   1   2   3   4   5
        
        // STATION A
        //
        //    |-- a0
        //    |   |
        //    |-- a2_
        // a1-|   |   \ _ a3 (120)
        //    |-- a4_
        //            \_ a5  (3720)
        //
        
        int a0 = 0; // Entry Node
        graph.setNode(0, 0, 1.0001f);
        int a1 = 1; // Exit Node
        graph.setNode(1, 0, 1.0001f);
        int a2 = 2;
        graph.setNode(2, 0, 1.0001f);
        int a3 = 3;
        graph.setNode(3, 0, 1.0001f);
        int a4 = 4;
        graph.setNode(4, 0, 1.0001f);
        int a5 = 5;
        graph.setNode(5, 0, 1.0001f);
        
        
        
        graph.edge(a0, a2, 120, entry);
        graph.edge(a2, a4, 3600, transit);
        
        graph.edge(a2, a3, 0, boarding);
        graph.edge(a4, a5, 0, boarding);
        
        graph.edge(a0, a1, 0, exit);
        graph.edge(a2, a1, 0, exit);
        graph.edge(a4, a1, 0, exit);
        
        // STATION B
        //
        //                b07
        //  *a3*--- b10_   |
        //              \_b09_
        //                 |   \_ b11 (1560s)
        //  *a5*--- b12_   |    _ b14 (3480s)
        //              \_b13_ /
        //                        
        // 
        //
        // (Exit node not drawn)
        
        int b7 = 7; // Entry Node
        graph.setNode(7, 1, 2);
        int b8 = 8; // Exit Node
        graph.setNode(8, 1, 2);
        int b9 = 9;
        graph.setNode(9, 1, 2);
        int b10 = 10;
        graph.setNode(10, 1, 2);
        int b11 = 11;
        graph.setNode(11, 1, 2);
        int b12 = 12;
        graph.setNode(12, 1, 2);
        int b13 = 13;
        graph.setNode(13, 1, 2);
        int b14 = 14;
        graph.setNode(14, 1, 2);
        
        
        // Travel Edges
        graph.edge(a3, b10, 1200, travel);
        graph.edge(a5, b12, 1320, travel);
        
        // Aligth Edges
        graph.edge(b10, b9, 240, alight);
        graph.edge(b12, b13, 240, alight);
        graph.edge(b14, b13, 240, alight);
        
        // Transit Edges
        graph.edge(b7, b9, 1560 , entry);
        graph.edge(b9, b13, 2160, transit);
        
        // Exit Edges
        graph.edge(b7, b8, 0, exit);
        graph.edge(b9, b8, 0, exit);
        graph.edge(b13, b8, 0, exit);
        
        // Boarding Edges
        graph.edge(b9, b11, 0, boarding);
        
        // STATION C
        //
        //                c15
        //                 |
        //               _c17
        // *b14*--- c18_/  |
        //                 |
        // *b11*--- c19_   | 
        //              \_c20 
        // 
        //
        // (Exit node not drawn)
        
        int c15 = 15;
        graph.setNode(15, 0.5f, 4.5f);
        int c16 = 16;
        graph.setNode(16, 0.5f, 4.5f);
        int c17 = 17;
        graph.setNode(17, 0.5f, 4.5f);
        int c18 = 18;
        graph.setNode(18, 0.5f, 4.5f);
        int c19 = 19;
        graph.setNode(19, 0.5f, 4.5f);
        int c20 = 20;
        graph.setNode(20, 0.5f, 4.5f);
        
        // Boarding Edge
        graph.edge(c17, c18, 0, boarding);
        
        // Alight Edge
        graph.edge(c19, c20, 240, alight);
        
                
        // Travel Edges
        graph.edge(c18, b14, 1000, travel);
        graph.edge(b11, c19, 1200, travel);
        
        // Transit Edges
        graph.edge(c15, c17, 2480, entry);
        graph.edge(c17, c20, 520, transit);
        
        // Exit Edges
        graph.edge(c15, c16, 0, exit);
        graph.edge(c17, c16, 0, exit);
        
        return graph;
    }
}