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

package de.bmw.hmm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;


/**
 *  Implementation of the Viterbi algorithm for time-inhomogeneous Markov processes.
 *  Uses logarithmic probabilities to prevent arithmetic underflows for small probability values.
 *  The plain Viterbi algorithm for stationary Markov processes is described e.g. in
 *  Rabiner, Juang, An introduction to Hidden Markov Models, IEEE ASSP Mag., pp 4-16, June 1986.
 *
 * @param <S> state class/interface
 * @param <O> observation class/interface
 */
public class ViterbiAlgorithm<S, O> {

    private class ForwardStepResult {
        /**
         * Log probability of each state. See {@link MostLikelySequence#messageHistory}.
         */
        final Map<S, Double> message;

        /**
         * Back pointers to previous state candidates for retrieving the most likely sequence after
         * the forward pass. See {@link MostLikelySequence#backPointerSequence}
         */
        final Map<S, S> backPointers;

        ForwardStepResult(int numberStates) {
            message = new HashMap<>(Utils.initialHashMapCapacity(numberStates));
            backPointers = new HashMap<>(Utils.initialHashMapCapacity(numberStates));
        }
    }


    /**
     * @see Hmm#computeMostLikelySequence(HmmProbabilities, Iterator)
     *
     * @param keepMessageHistory Whether to store intermediate forward messages.
     */
    public MostLikelySequence<S, O> compute(HmmProbabilities<S, O> hmmProbabilities,
            Iterator<TimeStep<S, O>> timeStepIter, boolean keepMessageHistory) {
        if (hmmProbabilities == null || timeStepIter == null) {
            throw new NullPointerException(
                    "hmmProbabilities and stepSequenceIter must not be null.");
        }

        // Filled in the remainder of this method.
        final List<Map<S, S>> backPointerSequence = new ArrayList<>();
        List<Map<S, Double>> messageHistory = null;
        if (keepMessageHistory) {
            messageHistory = new ArrayList<>();
        }

        /*
         *  Return empty most likely sequence if there are no time steps. This does not count
         *  as an HMM break.
         */
        if (!timeStepIter.hasNext()) {
            return new MostLikelySequence<S, O>(new ArrayList<S>(), false, backPointerSequence,
                    messageHistory);
        }

        /*
         * Compute initial log probability for each state in the forward message.
         * See Result.messageHistory.
         */
        TimeStep<S, O> timeStep = timeStepIter.next();
        Map<S, Double> message = computeInitalMessage(hmmProbabilities, timeStep);
        if (hmmBreak(message)) {
            return new MostLikelySequence<S, O>(new ArrayList<S>(), true, backPointerSequence,
                    messageHistory);
        }
        if (keepMessageHistory) {
            messageHistory.add(message);
        }

        // Forward pass
        boolean isBroken = false;
        while (timeStepIter.hasNext()) {
            final TimeStep<S, O> prevTimeStep = timeStep;
            timeStep = timeStepIter.next();
            ForwardStepResult forwardStepResult = forwardStep(hmmProbabilities, prevTimeStep,
                    timeStep, message);
            if (hmmBreak(forwardStepResult.message)) {
                isBroken = true;
                break;
            }
            if (keepMessageHistory) {
                messageHistory.add(forwardStepResult.message);
            }
            message = forwardStepResult.message;
            backPointerSequence.add(forwardStepResult.backPointers);
        }

        // Retrieve most likely state sequence
        final List<S> mostLikelySequence =
                retrieveMostLikelySequence(backPointerSequence, mostLikelyState(message));

        return new MostLikelySequence<S, O>(mostLikelySequence, isBroken, backPointerSequence,
                messageHistory);
    }

    /**
     * Computes initial log probability for each start state candidate based on first observation.
     */
    private Map<S, Double> computeInitalMessage(HmmProbabilities<S, O> hmmProbabilities,
            TimeStep<S, O> firstTimeStep) {
        Map<S, Double> message = new HashMap<>();
        for (S state : firstTimeStep.candidates) {
            message.put(state,
                    hmmProbabilities.emissionLogProbability(state, firstTimeStep.observation));
        }
        return message;
    }

    /**
     * Returns whether the specified message is either empty or only contains state candidates
     * with 0 probability and thus causes the HMM to break.
     */
    private boolean hmmBreak(Map<S, Double> message) {
        for (double logProbability : message.values()) {
            if (logProbability != Double.NEGATIVE_INFINITY) {
                return false;
            }
        }
        return true;
    }

    /**
     * Computes the new forward message and the back pointers to the previous states (next entry of
     * backPointerSequence).
     */
    private ForwardStepResult forwardStep(HmmProbabilities<S, O> hmmProbabilities,
            TimeStep<S, O> prevTimeStep, TimeStep<S, O> curTimeStep, Map<S, Double> message) {
        final ForwardStepResult result = new ForwardStepResult(curTimeStep.candidates.size());
        assert( !prevTimeStep.candidates.isEmpty());

        for (S curState : curTimeStep.candidates) {
            double maxLogProbability = Double.NEGATIVE_INFINITY;
            S maxPrevState = null;
            for (S prevState : prevTimeStep.candidates) {
                double logProbability = message.get(prevState)
                        + hmmProbabilities.transitionLogProbability(prevState, prevTimeStep.observation, curState, curTimeStep.observation);
                if (logProbability > maxLogProbability) {
                    maxLogProbability = logProbability;
                    maxPrevState = prevState;
                }
            }
            result.message.put(curState, maxLogProbability
                    + hmmProbabilities.emissionLogProbability(curState, curTimeStep.observation));
            result.backPointers.put(curState, maxPrevState);
        }
        return result;
    }


    /**
     * Retrieves a state with maximum probability.
     */
    private S mostLikelyState(Map<S, Double> message) {
        // Set first state as most likely state.
        final Iterator<Map.Entry<S, Double>> entryIter = message.entrySet().iterator();
        assert(entryIter.hasNext()); // Checked in compute.
        final Map.Entry<S, Double> firstEntry = entryIter.next();
        S result = firstEntry.getKey();
        double maxLogProbability = firstEntry.getValue();

        // Check remaining states.
        while (entryIter.hasNext()) {
            final Map.Entry<S, Double> entry = entryIter.next();
            if (entry.getValue() > maxLogProbability) {
                maxLogProbability = entry.getValue();
                result = entry.getKey();
            }
        }
        return result;
    }

    /**
     * Retrieves most likely sequence from specified back pointer sequence ending in the specified
     * last state. The result is stored in the passed empty mostLikelySequence.
     */
    private List<S> retrieveMostLikelySequence(List<Map<S, S>> backPointerSequence, S lastState) {
        final List<S> mostLikelySequence = new ArrayList<>();
        // Retrieve most likely state sequence in reverse order
        mostLikelySequence.add(lastState);

        ListIterator<Map<S, S>> backPointerSeqIter =
                backPointerSequence.listIterator(backPointerSequence.size());
        while(backPointerSeqIter.hasPrevious()) {
            lastState = backPointerSeqIter.previous().get(lastState);
            mostLikelySequence.add(lastState);
        }

        Collections.reverse(mostLikelySequence);
        return mostLikelySequence;
    }


}
