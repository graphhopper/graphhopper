/**
 * Copyright (C) 2015, BMW Car IT GmbH
 * Author: Stefan Holder (stefan.holder@bmw.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.graphhopper.matching;



/**
 * Provides spatial metrics needed to compute emission and transition probabilities.
 * This interface decouples from the actual metric computation and allows to either
 * precompute metrics (possibly remotely, e.g. using PostGIS) or to compute the metrics on
 * demand with an appropriate spatial library.
 *
 * @param <S> road position type, which corresponds to the HMM state.
 * @param <O> location measurement type, which corresponds to the HMM observation.
 */
interface SpatialMetrics<S, O> {

    /**
     * Returns the linear distance [m] between the specified location measurement and the specified
     * road position.
     */
    public double measurementDistance(S roadPosition, O measurement);

    /**
     * Returns the linear distance [m] between the specified subsequent location measurements.
     */
    public double linearDistance(O formerMeasurement, O laterMeasurement);

    /**
     * Returns the length [m] of the shortest route between both specified road positions or
     * null if no route exists between the specified road positions.
     */
    public Double routeLength(S sourcePosition, S targetPosition);
}
