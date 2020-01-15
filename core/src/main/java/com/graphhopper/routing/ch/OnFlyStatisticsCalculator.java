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
package com.graphhopper.routing.ch;

/**
 * Helper class for mean and variance calculation of a running sample
 * For reference see:
 * https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance
 */
class OnFlyStatisticsCalculator {

    private long count;
    private double mean;
    private double varianceHelper;

    void addObservation(long value) {
        count++;
        double delta = value - mean;
        mean += delta / count;
        double newDelta = value - mean;
        varianceHelper += delta * newDelta;
    }

    public long getCount() {
        return count;
    }

    double getMean() {
        return mean;
    }

    double getVariance() {
        return varianceHelper / count;
    }

    void reset() {
        count = 0;
        mean = 0;
        varianceHelper = 0;
    }
}
