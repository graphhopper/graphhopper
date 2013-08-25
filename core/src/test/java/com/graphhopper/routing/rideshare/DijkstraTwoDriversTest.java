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
package com.graphhopper.routing.rideshare;

import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.ShortestCalc;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.Graph;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class DijkstraTwoDriversTest
{
    CarFlagEncoder carEncoder = (CarFlagEncoder) new EncodingManager("CAR").getEncoder("CAR");

    Graph getGraph()
    {
        return AbstractRoutingAlgorithmTester.getMatrixAlikeGraph();
    }

    @Test
    public void testFindMeetingPointWhenNotCrossing()
    {
        Graph g = getGraph();
        WeightCalculation type = new ShortestCalc();
        DijkstraTwoDrivers d = new DijkstraTwoDrivers(g, carEncoder, type);

        d.setDriverA(12, 36);
        d.setDriverB(30, 45);
        d.calcPath();

        double shortest = Double.MAX_VALUE;
        TIntHashSet set = new TIntHashSet();        
        for (int pointI = 10; pointI < 50; pointI++)
        {
            double sum = new DijkstraBidirectionRef(g, carEncoder, type).calcPath(12, pointI).getWeight();
            sum += new DijkstraBidirectionRef(g, carEncoder, type).calcPath(pointI, 36).getWeight();
            sum += new DijkstraBidirectionRef(g, carEncoder, type).calcPath(30, pointI).getWeight();
            sum += new DijkstraBidirectionRef(g, carEncoder, type).calcPath(pointI, 45).getWeight();
            if (sum < shortest)
            {
                shortest = sum;
                set.clear();
                set.add(pointI);
            } else if (sum == shortest)
            {
                set.add(pointI);
            }
        }

        assertEquals(shortest, d.getBestForA().getWeight() + d.getBestForB().getWeight(), 1e-5);
        assertTrue("meeting points " + set.toString() + " do not contain " + d.getMeetingPoint(),
                set.contains(d.getMeetingPoint()));
    }

    @Test
    public void testFindMeetingPointWhenCrossing()
    {
        Graph g = getGraph();
        WeightCalculation type = new ShortestCalc();
        DijkstraTwoDrivers d = new DijkstraTwoDrivers(g, carEncoder, type);
        d.setDriverA(12, 36);
        d.setDriverB(30, 15);
        d.calcPath();

        Path pA = new DijkstraBidirectionRef(g, carEncoder, type).calcPath(12, 36);
        Path pB = new DijkstraBidirectionRef(g, carEncoder, type).calcPath(30, 15);
        TIntSet set = pA.calculateIdenticalNodes(pB);
        assertTrue(set.toString(), set.contains(d.getMeetingPoint()));
        assertEquals(pA.getWeight(), d.getBestForA().getWeight(), 1e-5);
        assertEquals(pB.getWeight(), d.getBestForB().getWeight(), 1e-5);
    }
}
