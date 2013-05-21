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
package com.graphhopper.routing;

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
        this.algorithm.type(new ShortestCalc());
    }
    
    public Path calPath(int from, int to, int timeOffset){
        Path path = this.algorithm.calcPath(from, to);
        path.distance += timeOffset;
        return path;
    }

    @Override
    public Path calcPath(int from, int to) {
        return algorithm.calcPath(from, to);
    }

    @Override
    public RoutingAlgorithm type(WeightCalculation calc) {
        return algorithm.type(calc);
    }

    @Override
    public String name() {
        return "publictranport_" + algorithm.name();
    }

    @Override
    public int visitedNodes() {
        return algorithm.visitedNodes();
    }
    
}
