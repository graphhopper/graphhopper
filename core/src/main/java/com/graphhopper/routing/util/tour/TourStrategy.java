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
package com.graphhopper.routing.util.tour;

import java.util.Random;

/**
 * Defines the strategy of creating tours.
 *
 * @author Robin Boldt
 */
public abstract class TourStrategy {
    protected final Random random;
    protected final double overallDistance;

    public TourStrategy(Random random, double distanceInMeter) {
        this.random = random;
        this.overallDistance = distanceInMeter;
    }

    /**
     * Defines the number of points that are generated
     */
    public abstract int getNumberOfGeneratedPoints();

    /**
     * Returns the distance in meter that is used for the generated point of a certain iteration
     */
    public abstract double getDistanceForIteration(int iteration);

    /**
     * Returns the north based heading between 0 and 360 for a certain iteration.
     */
    public abstract double getHeadingForIteration(int iteration);

    /**
     * Modifies the Distance up to +-10%
     */
    protected double slightlyModifyDistance(double distance) {
        double distanceModification = random.nextDouble() * .1 * distance;
        if (random.nextBoolean())
            distanceModification = -distanceModification;
        return distance + distanceModification;
    }
}
