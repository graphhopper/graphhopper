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

import de.bmw.hmm.HmmProbabilities;
import de.bmw.hmm.TimeStep;

import java.util.List;

/**
 * Based on Newson, Paul, and John Krumm. "Hidden Markov map matching through noise and sparseness."
 * Proceedings of the 17th ACM SIGSPATIAL International Conference on Advances in Geographic
 * Information Systems. ACM, 2009.
 *
 * @param <S> road position type, which corresponds to the HMM state.
 * @param <O> location measurement type, which corresponds to the HMM observation.
 */
class MapMatchingHmmProbabilities<S, O> implements HmmProbabilities<S, O> {

    private final SpatialMetrics<S, O> spatialMetrics;
    private final TemporalMetrics<O> temporalMetrics;
    private final double measurementErrorSigma;
    private final double transitionProbabilityBeta;

    public MapMatchingHmmProbabilities(List<TimeStep<S, O>> timeSteps,
                                       SpatialMetrics<S, O> spatialMetrics, TemporalMetrics<O> temporalMetrics,
                                       double measurementErrorSigma, double transitionProbabilityBeta) {
        if (timeSteps == null || spatialMetrics == null || temporalMetrics == null) {
            throw new NullPointerException();
        }
        this.spatialMetrics = spatialMetrics;
        this.temporalMetrics = temporalMetrics;
        this.measurementErrorSigma = measurementErrorSigma;
        this.transitionProbabilityBeta = transitionProbabilityBeta;
    }

    /**
     * Returns the logarithmic emission probability density.
     */
    @Override
    public double emissionLogProbability(S roadPosition, O measurement) {
        return Math.log(Distributions.normalDistribution(
                measurementErrorSigma,
                spatialMetrics.measurementDistance(roadPosition, measurement)));
    }

    /**
     * Returns the logarithmic transition probability density.
     */
    @Override
    public double transitionLogProbability(S sourcePosition, O sourceMeasurement, S targetPosition, O targetMeasurement) {
        Double transitionMetric = normalizedTransitionMetric(sourcePosition, sourceMeasurement, targetPosition, targetMeasurement);
        if (transitionMetric == null) {
            return Double.NEGATIVE_INFINITY;
        } else {
            return Distributions.logExponentialDistribution(
                    transitionProbabilityBeta, transitionMetric);
        }
    }

    /**
     * Returns |linearDistance - shortestRouteLength| / time_difference² in [m/s²], where
     * linearDistance is the linear distance between the corresponding location measurements of
     * sourcePositon and targetPosition, shortestRouteLength is the shortest route length from
     * sourcePosition to targetPosition on the road network and timeDifference is the time
     * difference between the corresponding location measurements of sourcePosition and
     * targetPosition.
     *
     * Returns null if there is no route between sourcePosition and targetPosition.
     *
     * In contrast to Newson & Krumm the absolute distance difference is divided by the quadratic
     * time difference to make the beta parameter of the exponential distribution independent of the
     * sampling interval.
     */
    public Double normalizedTransitionMetric(S sourcePosition, O sourceMeasurement, S targetPosition, O targetMeasurement) {
        final double timeDiff = temporalMetrics.timeDifference(sourceMeasurement, targetMeasurement);
        if (timeDiff < 0.0) {
            throw new IllegalStateException(
                    "Time difference between subsequent location measurements must be >= 0.");
        }

        final double linearDistance = spatialMetrics.linearDistance(sourceMeasurement,
                targetMeasurement);
        final Double routeLength = spatialMetrics.routeLength(sourcePosition, targetPosition);
        if (routeLength == null) {
            return null;
        } else {
            return Math.abs(linearDistance - routeLength) / (timeDiff * timeDiff);
        }
    }


}
