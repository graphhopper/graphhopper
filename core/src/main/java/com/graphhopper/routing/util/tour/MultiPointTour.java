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
 * Generate only a single points
 *
 * @author Robin Boldt
 */
public class MultiPointTour extends TourStrategy {
    private final int allPoints;
    private final double initialHeading;

    public MultiPointTour(Random random, double distanceInMeter, int allPoints) {
        this(random, distanceInMeter, allPoints, Double.NaN);
    }

    public MultiPointTour(Random random, double distanceInMeter, int allPoints, double initialHeading) {
        super(random, distanceInMeter);
        this.allPoints = allPoints;
        if (Double.isNaN(initialHeading))
            this.initialHeading = random.nextInt(360);
        else
            this.initialHeading = initialHeading;
    }

    @Override
    public int getNumberOfGeneratedPoints() {
        return allPoints - 1;
    }

    @Override
    public double getDistanceForIteration(int iteration) {
        return slightlyModifyDistance(overallDistance / (allPoints + 1));
    }

    @Override
    public double getHeadingForIteration(int iteration) {
        if (iteration == 0)
            return initialHeading;

        return initialHeading + 360.0 * iteration / allPoints;
    }
}
