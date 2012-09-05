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

import de.jetsli.graph.routing.AlgoType;
import de.jetsli.graph.util.EdgeIterator;

/**
 * @author Peter Karich
 */
public class WeightCalculation {

    public static WeightCalculation SHORTEST = new WeightCalculation(AlgoType.SHORTEST);
    public static WeightCalculation FASTEST = new WeightCalculation(AlgoType.FASTEST);
    private AlgoType type = AlgoType.SHORTEST;

    public WeightCalculation() {
    }

    public WeightCalculation(AlgoType type) {
        this.type = type;
    }

    public double getWeight(EdgeIterator iter) {
        if (AlgoType.FASTEST.equals(type)) {
            return iter.distance() / EdgeFlags.getSpeedPart(iter.flags());
        } else
            return iter.distance();
    }

    public double apply(double currDistToGoal) {
        if (AlgoType.FASTEST.equals(type))
            return currDistToGoal / EdgeFlags.MAX_SPEED;
        return currDistToGoal;
    }

    public double apply(double currDistToGoal, int flags) {
        if (AlgoType.FASTEST.equals(type))
            return currDistToGoal / EdgeFlags.getSpeedPart(flags);
        return currDistToGoal;
    }

    @Override public String toString() {
        return type.toString();
    }
}
