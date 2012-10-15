/*
 *  Copyright 2012 Peter Karich 
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
package com.graphhopper.routing.util;

import com.graphhopper.util.EdgeIterator;

/**
 * Specifies how the best route is calculated. E.g. the fastest or shortest route.
 *
 * @author Peter Karich
 */
public interface WeightCalculation {

    double getWeight(EdgeIterator iter);

    double apply(double currDistToGoal);

    double apply(double currDistToGoal, int flags);

    double revert(double weight, int flags);
}
