/**
 * Copyright (C) 2015-2016, BMW Car IT GmbH and BMW AG
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
 * Based on Newson, Paul, and John Krumm. "Hidden Markov map matching through
 * noise and sparseness." Proceedings of the 17th ACM SIGSPATIAL International
 * Conference on Advances in Geographic Information Systems. ACM, 2009.
 */
public class HmmProbabilities {

    private final double sigma;
    private final double beta;

    /**
     * @param sigma standard deviation of the normal distribution [m] used for
     *              modeling the GPS error
     * @param beta  beta parameter of the exponential distribution used for modeling
     *              transition probabilities
     */
    public HmmProbabilities(double sigma, double beta) {
        this.sigma = sigma;
        this.beta = beta;
    }

    /**
     * Returns the logarithmic emission probability density.
     *
     * @param distance Absolute distance [m] between GPS measurement and map
     *                 matching candidate.
     */
    public double emissionLogProbability(double distance) {
        return Distributions.logNormalDistribution(sigma, distance);
    }

    /**
     * Returns the logarithmic transition probability density for the given
     * transition parameters.
     *
     * @param routeLength    Length of the shortest route [m] between two
     *                       consecutive map matching candidates.
     * @param linearDistance Linear distance [m] between two consecutive GPS
     *                       measurements.
     */
    public double transitionLogProbability(double routeLength, double linearDistance) {
        // Transition metric taken from Newson & Krumm.
        double transitionMetric = Math.abs(linearDistance - routeLength);
        return Distributions.logExponentialDistribution(beta, transitionMetric);
    }

}
