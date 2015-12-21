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
package com.graphhopper.routing;

import com.graphhopper.routing.util.*;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.Helper;
import java.util.List;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class RoundTripAltAlgorithmTest
{
    private final FlagEncoder carFE = new CarFlagEncoder();
    private final EncodingManager em = new EncodingManager(carFE);
    // TODO private final TraversalMode tMode = TraversalMode.EDGE_BASED_2DIR;
    private final TraversalMode tMode = TraversalMode.NODE_BASED;

    @Test
    public void testCalcRoundTrip() throws Exception
    {
        Weighting weighting = new FastestWeighting(carFE);
        Graph g = createTestGraph(true);
        RoundTripAltAlgorithm rtAlgo = new RoundTripAltAlgorithm(g, carFE, weighting, tMode);
        double maxDist = Helper.DIST_EARTH.calcDist(0, 0, 0.05, 0.25) * 2;
        rtAlgo.setMaxWeightFactor(2);
        List<Path> paths = rtAlgo.calcRoundTrips(5, maxDist, 1.2);
        assertEquals(2, paths.size());
        assertEquals(Helper.createTList(5, 6, 3, 4), paths.get(0).calcNodes());
        assertEquals(Helper.createTList(4, 8, 7, 6, 5), paths.get(1).calcNodes());

        rtAlgo = new RoundTripAltAlgorithm(g, carFE, weighting, tMode);
        rtAlgo.setMaxWeightFactor(2);
        paths = rtAlgo.calcRoundTrips(6, maxDist, 2);
        assertEquals(2, paths.size());
        assertEquals(Helper.createTList(6, 3, 4), paths.get(0).calcNodes());
        assertEquals(Helper.createTList(4, 8, 7, 6), paths.get(1).calcNodes());

        rtAlgo = new RoundTripAltAlgorithm(g, carFE, weighting, tMode);
        rtAlgo.setMaxWeightFactor(2);
        paths = rtAlgo.calcRoundTrips(6, maxDist, 1);
        assertEquals(2, paths.size());
        assertEquals(Helper.createTList(6, 3, 4), paths.get(0).calcNodes());
        assertEquals(Helper.createTList(4, 3, 6), paths.get(1).calcNodes());
    }

    // TODO how to select alternative when the second best is the 'bestForwardPath' backwards?
    @Ignore
    public void testCalcRoundTripWithBiggerPenalty() throws Exception
    {
        Weighting weighting = new FastestWeighting(carFE);
        Graph g = createTestGraph(true);
        double maxDist = Helper.DIST_EARTH.calcDist(0, 0, 0.05, 0.25) * 2;
        RoundTripAltAlgorithm rtAlgo = new RoundTripAltAlgorithm(g, carFE, weighting, tMode);
        rtAlgo.setMaxWeightFactor(2);
        List<Path> paths = rtAlgo.calcRoundTrips(6, maxDist, 2);
        assertEquals(2, paths.size());
        // here we get 6,3,4,10 as best forward and 10,4,8,7,6 as best backward but 10,4,3,6 is selected as it looks like the 'alternative'
        assertEquals(Helper.createTList(6, 3, 4), paths.get(0).calcNodes());
        assertEquals(Helper.createTList(4, 8, 7, 6), paths.get(1).calcNodes());
    }

    @Test
    public void testCalcRoundTripWhereAlreadyPlateauStartIsDifferentToBestRoute() throws Exception
    {
        // exception occured for 51.074194,13.705444
        Weighting weighting = new FastestWeighting(carFE);

        // now force that start of plateau of alternative is already different edge than optimal route
        Graph g = createTestGraph(false);
        RoundTripAltAlgorithm rtAlgo = new RoundTripAltAlgorithm(g, carFE, weighting, tMode);
        rtAlgo.setMaxWeightFactor(2);
        double maxDist = Helper.DIST_EARTH.calcDist(0, 0, 0.05, 0.25) * 2;
        List<Path> paths = rtAlgo.calcRoundTrips(5, maxDist, 1.4);
        assertEquals(2, paths.size());
        assertEquals(Helper.createTList(5, 6, 3, 4), paths.get(0).calcNodes());
        assertEquals(Helper.createTList(4, 8, 7, 6, 5), paths.get(1).calcNodes());
    }

    private Graph createTestGraph( boolean b )
    {
        return new AlternativeRouteTest(tMode).createTestGraph(b, em);
    }
}
