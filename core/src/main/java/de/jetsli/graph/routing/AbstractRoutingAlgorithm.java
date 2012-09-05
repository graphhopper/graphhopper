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
package de.jetsli.graph.routing;

import de.jetsli.graph.routing.util.WeightCalculation;
import de.jetsli.graph.storage.EdgeEntry;
import de.jetsli.graph.storage.Graph;

/**
 * @author Peter Karich
 */
public abstract class AbstractRoutingAlgorithm implements RoutingAlgorithm {

    protected Graph graph;
    protected WeightCalculation weightCalc = new WeightCalculation();

    public AbstractRoutingAlgorithm(Graph graph) {
        this.graph = graph;
    }

    @Override
    public RoutingAlgorithm setType(WeightCalculation wc) {
        this.weightCalc = wc;
        return this;
    }

    public void updateShortest(EdgeEntry shortestDE, int currLoc) {
    }

    @Override public RoutingAlgorithm clear() {
        return this;
    }

    @Override public String toString() {
        return getClass().getSimpleName() + "|" + weightCalc;
    }
}
