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
package com.graphhopper.routing.weighting;

/**
 * Specifies a weight approximation between an node and the goalNode according to the specified
 * weighting.
 *
 * @author jansoe
 * @author Peter Karich
 */
public interface WeightApproximator {
    /**
     * @return minimal weight of the specified currentNode to the 'to' node
     */
    double approximate(int currentNode);

    void setTo(int to);

    /**
     * Makes a 'reverse' copy of itself to make it possible using the two objects independent e.g.
     * on different threads. Do not copy state depending on the current approximate calls. 'reverse'
     * means the WeightApproximator should handle approximate calls towards the 'from' instead
     * towards the 'to'.
     */
    WeightApproximator reverse();
}
