/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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

import com.graphhopper.routing.weighting.BeelineWeightApproximator;
import com.graphhopper.routing.weighting.WeightApproximator;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;

import static com.graphhopper.util.Parameters.Algorithms.*;

/**
 * A simple factory creating normal algorithms (RoutingAlgorithm) without preparation.
 * <p>
 *
 * @author Peter Karich
 */
public class RoutingAlgorithmFactorySimple implements RoutingAlgorithmFactory {
    @Override
    public RoutingAlgorithm createAlgo(Graph g, Weighting w, AlgorithmOptions opts) {
        RoutingAlgorithm ra;
        String algoStr = opts.getAlgorithm();
        Weighting weighting = g.wrapWeighting(w);
        if (DIJKSTRA_BI.equalsIgnoreCase(algoStr)) {
            ra = new DijkstraBidirectionRef(g, weighting, opts.getTraversalMode());
        } else if (DIJKSTRA.equalsIgnoreCase(algoStr)) {
            ra = new Dijkstra(g, weighting, opts.getTraversalMode());

        } else if (ASTAR_BI.equalsIgnoreCase(algoStr) || Helper.isEmpty(algoStr)) {
            AStarBidirection aStarBi = new AStarBidirection(g, weighting,
                    opts.getTraversalMode());
            aStarBi.setApproximation(getApproximation(ASTAR_BI, opts.getHints(), weighting, g.getNodeAccess()));
            ra = aStarBi;

        } else if (DIJKSTRA_ONE_TO_MANY.equalsIgnoreCase(algoStr)) {
            ra = new DijkstraOneToMany(g, weighting, opts.getTraversalMode());

        } else if (ASTAR.equalsIgnoreCase(algoStr)) {
            AStar aStar = new AStar(g, weighting, opts.getTraversalMode());
            aStar.setApproximation(getApproximation(ASTAR, opts.getHints(), w, g.getNodeAccess()));
            ra = aStar;

        } else if (ALT_ROUTE.equalsIgnoreCase(algoStr)) {
            AlternativeRoute altRouteAlgo = new AlternativeRoute(g, weighting, opts.getTraversalMode(), opts.getHints());
            ra = altRouteAlgo;

        } else {
            throw new IllegalArgumentException("Algorithm " + algoStr + " not found in " + getClass().getName());
        }

        ra.setMaxVisitedNodes(opts.getMaxVisitedNodes());
        return ra;
    }

    public static WeightApproximator getApproximation(String prop, PMap opts, Weighting weighting, NodeAccess na) {
        String approxAsStr = opts.getString(prop + ".approximation", "BeelineSimplification");
        double epsilon = opts.getDouble(prop + ".epsilon", 1);

        BeelineWeightApproximator approx = new BeelineWeightApproximator(na, weighting);
        approx.setEpsilon(epsilon);
        if ("BeelineSimplification".equals(approxAsStr))
            approx.setDistanceCalc(DistancePlaneProjection.DIST_PLANE);
        else if ("BeelineAccurate".equals(approxAsStr))
            approx.setDistanceCalc(DistanceCalcEarth.DIST_EARTH);
        else
            throw new IllegalArgumentException("Approximation " + approxAsStr + " not found in " + RoutingAlgorithmFactorySimple.class.getName());

        return approx;
    }
}
