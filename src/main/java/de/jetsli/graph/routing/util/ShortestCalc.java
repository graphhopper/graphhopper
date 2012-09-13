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
package de.jetsli.graph.routing.util;

import de.jetsli.graph.util.EdgeIterator;

/**
 * @author Peter Karich
 */
public class ShortestCalc implements WeightCalculation {

    public static ShortestCalc DEFAULT = new ShortestCalc();

    @Override
    public double getWeight(EdgeIterator iter) {
        return iter.distance();
    }

    @Override
    public double apply(double currDistToGoal) {
        return currDistToGoal;
    }

    @Override
    public double apply(double currDistToGoal, int flags) {
        return currDistToGoal;
    }

    @Override public String toString() {
        return "SHORTEST";
    }
}
