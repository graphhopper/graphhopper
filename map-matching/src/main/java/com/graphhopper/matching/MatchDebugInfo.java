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
package com.graphhopper.matching;

import java.util.List;

public class MatchDebugInfo {

    public static class Candidate {
        public final int timeStep;
        public final int candidateIndex;
        public final double snappedLat;
        public final double snappedLon;
        public final double queryDistance;
        public final double emissionLogProbability;
        public final boolean directed;
        public final int closestNode;
        public final boolean chosen;

        public Candidate(int timeStep, int candidateIndex, double snappedLat, double snappedLon,
                         double queryDistance, double emissionLogProbability, boolean directed,
                         int closestNode, boolean chosen) {
            this.timeStep = timeStep;
            this.candidateIndex = candidateIndex;
            this.snappedLat = snappedLat;
            this.snappedLon = snappedLon;
            this.queryDistance = queryDistance;
            this.emissionLogProbability = emissionLogProbability;
            this.directed = directed;
            this.closestNode = closestNode;
            this.chosen = chosen;
        }
    }

    public static class TimeStepInfo {
        public final int index;
        public final double observationLat;
        public final double observationLon;
        public final List<Candidate> candidates;

        public TimeStepInfo(int index, double observationLat, double observationLon, List<Candidate> candidates) {
            this.index = index;
            this.observationLat = observationLat;
            this.observationLon = observationLon;
            this.candidates = candidates;
        }
    }

    public static class TransitionInfo {
        public final int fromTimeStep;
        public final int fromCandidateIndex;
        public final int toTimeStep;
        public final int toCandidateIndex;
        public final double transitionLogProbability;
        public final double routeDistance;
        public final double linearDistance;
        public final boolean chosen;
        public final List<double[]> routeGeometry;
        public final boolean uTurn;
        public final double uTurnCost;

        public TransitionInfo(int fromTimeStep, int fromCandidateIndex,
                              int toTimeStep, int toCandidateIndex,
                              double transitionLogProbability, double routeDistance,
                              double linearDistance, boolean chosen,
                              List<double[]> routeGeometry,
                              boolean uTurn, double uTurnCost) {
            this.fromTimeStep = fromTimeStep;
            this.fromCandidateIndex = fromCandidateIndex;
            this.toTimeStep = toTimeStep;
            this.toCandidateIndex = toCandidateIndex;
            this.transitionLogProbability = transitionLogProbability;
            this.routeDistance = routeDistance;
            this.linearDistance = linearDistance;
            this.chosen = chosen;
            this.routeGeometry = routeGeometry;
            this.uTurn = uTurn;
            this.uTurnCost = uTurnCost;
        }
    }

    public final double measurementErrorSigma;
    public final double transitionProbabilityBeta;
    public final List<double[]> originalObservations;
    public final List<double[]> filteredObservations;
    public final List<TimeStepInfo> timeSteps;
    public final List<TransitionInfo> transitions;
    public final List<double[]> matchedRoute;

    public MatchDebugInfo(double measurementErrorSigma, double transitionProbabilityBeta,
                          List<double[]> originalObservations, List<double[]> filteredObservations,
                          List<TimeStepInfo> timeSteps, List<TransitionInfo> transitions,
                          List<double[]> matchedRoute) {
        this.measurementErrorSigma = measurementErrorSigma;
        this.transitionProbabilityBeta = transitionProbabilityBeta;
        this.originalObservations = originalObservations;
        this.filteredObservations = filteredObservations;
        this.timeSteps = timeSteps;
        this.transitions = transitions;
        this.matchedRoute = matchedRoute;
    }
}
