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
package de.jetsli.graph.routing.rideshare;

import de.jetsli.graph.routing.AbstractRoutingAlgorithmTester;
import de.jetsli.graph.routing.DijkstraBidirection;
import de.jetsli.graph.routing.Path;
import de.jetsli.graph.storage.Graph;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class DijkstraTwoDriversTest {

    @Test public void testFindMeetingPointWhenNotCrossing() {
        Graph g = AbstractRoutingAlgorithmTester.matrixGraph;
        DijkstraTwoDrivers d = new DijkstraTwoDrivers(g);
        d.setDriverA(12, 36);
        d.setDriverB(30, 45);
        d.calcShortestPath();
        
        double shortest = Double.MAX_VALUE;
        TIntHashSet set = new TIntHashSet();
        for (int pointI = 10; pointI < 50; pointI++) {            
            double sum = new DijkstraBidirection(g).calcShortestPath(12, pointI).distance();
            sum += new DijkstraBidirection(g).calcShortestPath(pointI, 36).distance();
            
            sum += new DijkstraBidirection(g).calcShortestPath(30, pointI).distance();
            sum += new DijkstraBidirection(g).calcShortestPath(pointI, 45).distance();            
            if(sum < shortest) {
                shortest = sum;
                set.clear();
                set.add(pointI);
            } else if(sum == shortest)
                set.add(pointI);
        }
                
        assertEquals(shortest, d.getBestForA().distance() + d.getBestForB().distance(), 1e-5);
        assertTrue("meeting points " + set.toString() + " do not contain " + d.getMeetingPoint(), 
                set.contains(d.getMeetingPoint()));
    }
    
    @Test public void testFindMeetingPointWhenCrossing() {
        Graph g = AbstractRoutingAlgorithmTester.matrixGraph;
        DijkstraTwoDrivers d = new DijkstraTwoDrivers(g);
        d.setDriverA(12, 36);
        d.setDriverB(30, 15);
        d.calcShortestPath();
        
        Path pA = new DijkstraBidirection(g).calcShortestPath(12, 36);
        Path pB = new DijkstraBidirection(g).calcShortestPath(30, 15);        
        TIntSet set = pA.and(pB);
        assertTrue(set.toString(), set.contains(d.getMeetingPoint()));
        assertEquals(pA.distance(), d.getBestForA().distance(), 1e-5);        
        assertEquals(pB.distance(), d.getBestForB().distance(), 1e-5);        
    }
}
