/**
 * Copyright (C) 2015-2016, BMW Car IT GmbH and BMW AG
 * Author: Stefan Holder (stefan.holder@bmw.de)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bmw.hmm;

import org.junit.jupiter.api.Test;

import java.util.*;

import static java.lang.Math.log;
import static org.junit.jupiter.api.Assertions.*;

public class ViterbiAlgorithmTest {

    private static class Rain {
        final static Rain T = new Rain();
        final static Rain F = new Rain();

        @Override
        public String toString() {
            if (this == T) {
                return "Rain";
            } else if (this == F) {
                return "Sun";
            }
            throw new IllegalStateException();
        }
    }

    private static class Umbrella {
        final static Umbrella T = new Umbrella();
        final static Umbrella F = new Umbrella();

        @Override
        public String toString() {
            if (this == T) {
                return "Umbrella";
            } else if (this == F) {
                return "No umbrella";
            }
            throw new IllegalStateException();
        }
    }

    private static class Descriptor {
        final static Descriptor R2R = new Descriptor();
        final static Descriptor R2S = new Descriptor();
        final static Descriptor S2R = new Descriptor();
        final static Descriptor S2S = new Descriptor();

        @Override
        public String toString() {
            if (this == R2R) {
                return "R2R";
            } else if (this == R2S) {
                return "R2S";
            } else if (this == S2R) {
                return "S2R";
            } else if (this == S2S) {
                return "S2S";
            }
            throw new IllegalStateException();
        }
    }

    private static double DELTA = 1e-8;

    private List<Rain> states(List<SequenceState<Rain, Umbrella, Descriptor>> sequenceStates) {
        final List<Rain> result = new ArrayList<>();
        for (SequenceState<Rain, Umbrella, Descriptor> ss : sequenceStates) {
            result.add(ss.state);
        }
        return result;
    }

    /**
     * Tests the Viterbi algorithms with the umbrella example taken from Russell, Norvig: Aritifical
     * Intelligence - A Modern Approach, 3rd edition, chapter 15.2.3. Note that the probabilities in
     * Figure 15.5 are different, since the book uses initial probabilities and the probabilities
     * for message m1:1 are normalized (not wrong but unnecessary).
     */
    @Test
    public void testComputeMostLikelySequence() {
        final List<Rain> candidates = new ArrayList<>();
        candidates.add(Rain.T);
        candidates.add(Rain.F);

        final Map<Rain, Double> emissionLogProbabilitiesForUmbrella = new LinkedHashMap<>();
        emissionLogProbabilitiesForUmbrella.put(Rain.T, log(0.9));
        emissionLogProbabilitiesForUmbrella.put(Rain.F, log(0.2));

        final Map<Rain, Double> emissionLogProbabilitiesForNoUmbrella = new LinkedHashMap<>();
        emissionLogProbabilitiesForNoUmbrella.put(Rain.T, log(0.1));
        emissionLogProbabilitiesForNoUmbrella.put(Rain.F, log(0.8));

        final Map<Transition<Rain>, Double> transitionLogProbabilities = new LinkedHashMap<>();
        transitionLogProbabilities.put(new Transition<Rain>(Rain.T, Rain.T), log(0.7));
        transitionLogProbabilities.put(new Transition<Rain>(Rain.T, Rain.F), log(0.3));
        transitionLogProbabilities.put(new Transition<Rain>(Rain.F, Rain.T), log(0.3));
        transitionLogProbabilities.put(new Transition<Rain>(Rain.F, Rain.F), log(0.7));

        final Map<Transition<Rain>, Descriptor> transitionDescriptors = new LinkedHashMap<>();
        transitionDescriptors.put(new Transition<Rain>(Rain.T, Rain.T), Descriptor.R2R);
        transitionDescriptors.put(new Transition<Rain>(Rain.T, Rain.F), Descriptor.R2S);
        transitionDescriptors.put(new Transition<Rain>(Rain.F, Rain.T), Descriptor.S2R);
        transitionDescriptors.put(new Transition<Rain>(Rain.F, Rain.F), Descriptor.S2S);

        final ViterbiAlgorithm<Rain, Umbrella, Descriptor> viterbi = new ViterbiAlgorithm<>(true);
        viterbi.startWithInitialObservation(Umbrella.T, candidates,
                emissionLogProbabilitiesForUmbrella);
        viterbi.nextStep(Umbrella.T, candidates, emissionLogProbabilitiesForUmbrella,
                transitionLogProbabilities, transitionDescriptors);
        viterbi.nextStep(Umbrella.F, candidates, emissionLogProbabilitiesForNoUmbrella,
                transitionLogProbabilities, transitionDescriptors);
        viterbi.nextStep(Umbrella.T, candidates, emissionLogProbabilitiesForUmbrella,
                transitionLogProbabilities, transitionDescriptors);

        final List<SequenceState<Rain, Umbrella, Descriptor>> result =
                viterbi.computeMostLikelySequence();

        // Check most likely sequence
        assertEquals(4, result.size());
        assertEquals(Rain.T, result.get(0).state);
        assertEquals(Rain.T, result.get(1).state);
        assertEquals(Rain.F, result.get(2).state);
        assertEquals(Rain.T, result.get(3).state);

        assertEquals(Umbrella.T, result.get(0).observation);
        assertEquals(Umbrella.T, result.get(1).observation);
        assertEquals(Umbrella.F, result.get(2).observation);
        assertEquals(Umbrella.T, result.get(3).observation);

        assertEquals(null, result.get(0).transitionDescriptor);
        assertEquals(Descriptor.R2R, result.get(1).transitionDescriptor);
        assertEquals(Descriptor.R2S, result.get(2).transitionDescriptor);
        assertEquals(Descriptor.S2R, result.get(3).transitionDescriptor);

        // Check for HMM breaks
        assertFalse(viterbi.isBroken());

        // Check message history
        List<Map<Rain, Double>> expectedMessageHistory = new ArrayList<>();
        Map<Rain, Double> message = new LinkedHashMap<>();
        message.put(Rain.T, 0.9);
        message.put(Rain.F, 0.2);
        expectedMessageHistory.add(message);

        message = new LinkedHashMap<>();
        message.put(Rain.T, 0.567);
        message.put(Rain.F, 0.054);
        expectedMessageHistory.add(message);

        message = new LinkedHashMap<>();
        message.put(Rain.T, 0.03969);
        message.put(Rain.F, 0.13608);
        expectedMessageHistory.add(message);

        message = new LinkedHashMap<>();
        message.put(Rain.T, 0.0367416);
        message.put(Rain.F, 0.0190512);
        expectedMessageHistory.add(message);

        List<Map<Rain, Double>> actualMessageHistory = viterbi.messageHistory();
        checkMessageHistory(expectedMessageHistory, actualMessageHistory);
    }

    private void checkMessageHistory(List<Map<Rain, Double>> expectedMessageHistory,
                                     List<Map<Rain, Double>> actualMessageHistory) {
        assertEquals(expectedMessageHistory.size(), actualMessageHistory.size());
        for (int i = 0; i < expectedMessageHistory.size(); i++) {
            checkMessage(expectedMessageHistory.get(i), actualMessageHistory.get(i));
        }
    }

    private void checkMessage(Map<Rain, Double> expectedMessage, Map<Rain, Double> actualMessage) {
        assertEquals(expectedMessage.size(), actualMessage.size());
        for (Map.Entry<Rain, Double> entry : expectedMessage.entrySet()) {
            assertEquals(entry.getValue(), Math.exp(actualMessage.get(entry.getKey())), DELTA);
        }
    }

    @Test
    public void testEmptySequence() {
        final ViterbiAlgorithm<Rain, Umbrella, Descriptor> viterbi = new ViterbiAlgorithm<>();
        final List<SequenceState<Rain, Umbrella, Descriptor>> result =
                viterbi.computeMostLikelySequence();

        assertEquals(Arrays.asList(), result);
        assertFalse(viterbi.isBroken());
    }

    @Test
    public void testBreakAtInitialMessage() {
        final ViterbiAlgorithm<Rain, Umbrella, Descriptor> viterbi = new ViterbiAlgorithm<>();
        final List<Rain> candidates = new ArrayList<>();
        candidates.add(Rain.T);
        candidates.add(Rain.F);

        final Map<Rain, Double> emissionLogProbabilities = new LinkedHashMap<>();
        emissionLogProbabilities.put(Rain.T, log(0.0));
        emissionLogProbabilities.put(Rain.F, log(0.0));
        viterbi.startWithInitialObservation(Umbrella.T, candidates, emissionLogProbabilities);
        assertTrue(viterbi.isBroken());
        assertEquals(Arrays.asList(), viterbi.computeMostLikelySequence());
    }

    @Test
    public void testEmptyInitialMessage() {
        final ViterbiAlgorithm<Rain, Umbrella, Descriptor> viterbi = new ViterbiAlgorithm<>();
        viterbi.startWithInitialObservation(Umbrella.T, new ArrayList<Rain>(),
                new LinkedHashMap<Rain, Double>());
        assertTrue(viterbi.isBroken());
        assertEquals(Arrays.asList(), viterbi.computeMostLikelySequence());
    }

    @Test
    public void testBreakAtFirstTransition() {
        final ViterbiAlgorithm<Rain, Umbrella, Descriptor> viterbi = new ViterbiAlgorithm<>();
        final List<Rain> candidates = new ArrayList<>();
        candidates.add(Rain.T);
        candidates.add(Rain.F);

        final Map<Rain, Double> emissionLogProbabilities = new LinkedHashMap<>();
        emissionLogProbabilities.put(Rain.T, log(0.9));
        emissionLogProbabilities.put(Rain.F, log(0.2));
        viterbi.startWithInitialObservation(Umbrella.T, candidates, emissionLogProbabilities);
        assertFalse(viterbi.isBroken());

        final Map<Transition<Rain>, Double> transitionLogProbabilities = new LinkedHashMap<>();
        transitionLogProbabilities.put(new Transition<Rain>(Rain.T, Rain.T), log(0.0));
        transitionLogProbabilities.put(new Transition<Rain>(Rain.T, Rain.F), log(0.0));
        transitionLogProbabilities.put(new Transition<Rain>(Rain.F, Rain.T), log(0.0));
        transitionLogProbabilities.put(new Transition<Rain>(Rain.F, Rain.F), log(0.0));
        viterbi.nextStep(Umbrella.T, candidates, emissionLogProbabilities,
                transitionLogProbabilities);

        assertTrue(viterbi.isBroken());
        assertEquals(Arrays.asList(Rain.T), states(viterbi.computeMostLikelySequence()));
    }

    @Test
    public void testBreakAtFirstTransitionWithNoCandidates() {
        final ViterbiAlgorithm<Rain, Umbrella, Descriptor> viterbi = new ViterbiAlgorithm<>();
        final List<Rain> candidates = new ArrayList<>();
        candidates.add(Rain.T);
        candidates.add(Rain.F);

        final Map<Rain, Double> emissionLogProbabilities = new LinkedHashMap<>();
        emissionLogProbabilities.put(Rain.T, log(0.9));
        emissionLogProbabilities.put(Rain.F, log(0.2));
        viterbi.startWithInitialObservation(Umbrella.T, candidates, emissionLogProbabilities);
        assertFalse(viterbi.isBroken());

        viterbi.nextStep(Umbrella.T, new ArrayList<Rain>(), new LinkedHashMap<Rain, Double>(),
                new LinkedHashMap<Transition<Rain>, Double>());
        assertTrue(viterbi.isBroken());

        assertEquals(Arrays.asList(Rain.T), states(viterbi.computeMostLikelySequence()));
    }

    @Test
    public void testBreakAtSecondTransition() {
        final ViterbiAlgorithm<Rain, Umbrella, Descriptor> viterbi = new ViterbiAlgorithm<>();
        final List<Rain> candidates = new ArrayList<>();
        candidates.add(Rain.T);
        candidates.add(Rain.F);

        final Map<Rain, Double> emissionLogProbabilities = new LinkedHashMap<>();
        emissionLogProbabilities.put(Rain.T, log(0.9));
        emissionLogProbabilities.put(Rain.F, log(0.2));
        viterbi.startWithInitialObservation(Umbrella.T, candidates, emissionLogProbabilities);
        assertFalse(viterbi.isBroken());

        Map<Transition<Rain>, Double> transitionLogProbabilities = new LinkedHashMap<>();
        transitionLogProbabilities.put(new Transition<Rain>(Rain.T, Rain.T), log(0.5));
        transitionLogProbabilities.put(new Transition<Rain>(Rain.T, Rain.F), log(0.5));
        transitionLogProbabilities.put(new Transition<Rain>(Rain.F, Rain.T), log(0.5));
        transitionLogProbabilities.put(new Transition<Rain>(Rain.F, Rain.F), log(0.5));
        viterbi.nextStep(Umbrella.T, candidates, emissionLogProbabilities,
                transitionLogProbabilities);
        assertFalse(viterbi.isBroken());

        transitionLogProbabilities = new LinkedHashMap<>();
        transitionLogProbabilities.put(new Transition<Rain>(Rain.T, Rain.T), log(0.0));
        transitionLogProbabilities.put(new Transition<Rain>(Rain.T, Rain.F), log(0.0));
        transitionLogProbabilities.put(new Transition<Rain>(Rain.F, Rain.T), log(0.0));
        transitionLogProbabilities.put(new Transition<Rain>(Rain.F, Rain.F), log(0.0));
        viterbi.nextStep(Umbrella.T, candidates, emissionLogProbabilities,
                transitionLogProbabilities);

        assertTrue(viterbi.isBroken());
        assertEquals(Arrays.asList(Rain.T, Rain.T), states(viterbi.computeMostLikelySequence()));
    }

    @Test
    /**
     * Checks if the first candidate is returned if multiple candidates are equally likely.
     */
    public void testDeterministicCandidateOrder() {
        final List<Rain> candidates = new ArrayList<>();
        candidates.add(Rain.T);
        candidates.add(Rain.F);

        // Reverse usual order of emission and transition probabilities keys since their order
        // should not matter.
        final Map<Rain, Double> emissionLogProbabilitiesForUmbrella = new LinkedHashMap<>();
        emissionLogProbabilitiesForUmbrella.put(Rain.F, log(0.5));
        emissionLogProbabilitiesForUmbrella.put(Rain.T, log(0.5));

        final Map<Rain, Double> emissionLogProbabilitiesForNoUmbrella = new LinkedHashMap<>();
        emissionLogProbabilitiesForNoUmbrella.put(Rain.F, log(0.5));
        emissionLogProbabilitiesForNoUmbrella.put(Rain.T, log(0.5));

        final Map<Transition<Rain>, Double> transitionLogProbabilities = new LinkedHashMap<>();
        transitionLogProbabilities.put(new Transition<Rain>(Rain.F, Rain.T), log(0.5));
        transitionLogProbabilities.put(new Transition<Rain>(Rain.F, Rain.F), log(0.5));
        transitionLogProbabilities.put(new Transition<Rain>(Rain.T, Rain.T), log(0.5));
        transitionLogProbabilities.put(new Transition<Rain>(Rain.T, Rain.F), log(0.5));

        final ViterbiAlgorithm<Rain, Umbrella, Descriptor> viterbi = new ViterbiAlgorithm<>(true);
        viterbi.startWithInitialObservation(Umbrella.T, candidates,
                emissionLogProbabilitiesForUmbrella);
        viterbi.nextStep(Umbrella.T, candidates, emissionLogProbabilitiesForUmbrella,
                transitionLogProbabilities);
        viterbi.nextStep(Umbrella.F, candidates, emissionLogProbabilitiesForNoUmbrella,
                transitionLogProbabilities);
        viterbi.nextStep(Umbrella.T, candidates, emissionLogProbabilitiesForUmbrella,
                transitionLogProbabilities);

        final List<SequenceState<Rain, Umbrella, Descriptor>> result =
                viterbi.computeMostLikelySequence();

        // Check most likely sequence
        assertEquals(4, result.size());
        assertEquals(Rain.T, result.get(0).state);
        assertEquals(Rain.T, result.get(1).state);
        assertEquals(Rain.T, result.get(2).state);
        assertEquals(Rain.T, result.get(3).state);
    }

}