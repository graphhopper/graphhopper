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
package com.graphhopper.routing.util.tour;

import java.util.Random;

/**
 * Defines the Strategy of creating Tours.
 *
 * @author Robin Boldt
 */
abstract class TourStrategy
{

    protected static Random random = new Random();
    protected double overallDistance;

    public TourStrategy(double overallDistance){
        this.overallDistance = overallDistance;
    }

    /**
     * Defines the number of points that are generated
     */
    abstract int numberOfGeneratedPoints();

    /**
     * Returns the distance in KM that is used for the generated point #iteration
     */
    abstract double getDistanceForIteration(int iteration);

    /**
     * Returns the bearing between 0 and 360 for the current #iteration
     */
    abstract double getBearingForIteration(int iteration);


    /**
     * Modifies the Distance up to +-10%
     */
    protected double slightlyModifyDistance(double distance){
        double distanceModification = random.nextDouble() * .1 * distance;
        if (random.nextBoolean())
            distanceModification = -distanceModification;
        return distance + distanceModification;
    }

    /**
     * Allows to add a value to the current bearing. If the value becomse greater than 360 start from 0.
     */
    protected double addToBearing(double bearing, double add){
        // Fix initial values
        add = add % 360;
        bearing = bearing % 360;

        bearing = bearing + add;
        if(bearing < 0)
            bearing = 360 + bearing; // + since the number is negative

        return bearing % 360;
    }

}
