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
package com.graphhopper.transit.routing;

import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.ShortestCalc;
import com.graphhopper.routing.util.WeightCalculation;

/**
 *
 * @author Thomas Buerli <tbuerli@student.ethz.ch>
 */
public class PublicTransitRouting implements RoutingAlgorithm{

    private RoutingAlgorithm algorithm;
    
    public PublicTransitRouting(RoutingAlgorithm algorithm) {
        this.algorithm = algorithm;
        this.algorithm.setType(new ShortestCalc());
    }
    
    public Path calcPath(int from, int to, int timeOffset){
        Path path = algorithm.calcPath(from, to);
        path.setWeight(path.getDistance() + timeOffset);
        return path;
    }

    @Override
    public Path calcPath(int from, int to) {
        return algorithm.calcPath(from, to);
    }

    @Override
    public RoutingAlgorithm setType(WeightCalculation calc) {
        return algorithm.setType(calc);
    }

    @Override
    public String getName() {
        return "publictranport_" + algorithm.getName();
    }

    @Override
    public int getVisitedNodes() {
        return algorithm.getVisitedNodes();
    }

}
