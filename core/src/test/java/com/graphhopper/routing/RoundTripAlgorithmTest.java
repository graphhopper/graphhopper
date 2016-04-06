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
import com.graphhopper.routing.util.tour.TourPointGenerator;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.Helper;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class RoundTripAlgorithmTest
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

        RoundTripAlgorithm rtAlgo = new RoundTripAlgorithm(g, carFE, weighting, tMode);
        rtAlgo.setTourPointGenerator(new TourPointGenerator()
        {
            @Override
            public List<Integer> calculatePoints()
            {
                return Arrays.asList(5, 4, 5);
            }
        });

        // double maxDist = Helper.DIST_EARTH.calcDist(0, 0, 0.05, 0.25) * 2;
        List<Path> paths = rtAlgo.calcRoundTrip();
        assertEquals(2, paths.size());
        assertEquals(Helper.createTList(5, 6, 3, 4), paths.get(0).calcNodes());
        assertEquals(Helper.createTList(4, 8, 7, 6, 5), paths.get(1).calcNodes());

        rtAlgo = new RoundTripAlgorithm(g, carFE, weighting, tMode);
        rtAlgo.setTourPointGenerator(new TourPointGenerator()
        {
            @Override
            public List<Integer> calculatePoints()
            {
                return Arrays.asList(6, 4, 6);
            }
        });

        paths = rtAlgo.calcRoundTrip();
        assertEquals(2, paths.size());
        assertEquals(Helper.createTList(6, 3, 4), paths.get(0).calcNodes());
        assertEquals(Helper.createTList(4, 8, 7, 6), paths.get(1).calcNodes());
    }

    private Graph createTestGraph( boolean fullGraph )
    {
        return new AlternativeRouteTest(tMode).createTestGraph(fullGraph, em);
    }
}
