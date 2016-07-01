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

import static java.lang.Math.log;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

class Rain {
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

class Umbrella {
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

class UmbrellaProbabilities implements HmmProbabilities<Rain, Umbrella> {

    @Override
    public double emissionLogProbability(Rain state, Umbrella observation) {
        if (state == Rain.T) {
            if (observation == Umbrella.T) {
                return log(0.9);
            } else {
                return log(0.1);
            }
        } else {
            if (observation == Umbrella.T) {
                return log(0.2);
            } else {
                return log(0.8);
            }
        }
    }

    @Override
    public double transitionLogProbability(Rain sourceState, Umbrella sourceObservation, Rain targetState, Umbrella targetObservation) {
        if (sourceState == Rain.T) {
            if (targetState == Rain.T) {
                return log(0.7);
            } else {
                return log(0.3);
            }
        } else {
            if (targetState == Rain.T) {
                return log(0.3);
            } else {
                return log(0.7);
            }
        }
    }

}

/**
 * Tests the Viterbi algorithms with the umbrella example taken from Russell, Norvig: Aritifical
 * Intelligence - A Modern Approach, 3rd edition, chapter 15.2.3. Note that the probabilities in
 * Figure 15.5 are different, since the book uses initial probabilities and the probabilities for
 * message m1:1 are normalized (not wrong but unnecessary).
 */
public class ViterbiAlgorithmUmbrellaTest {

    private static double DELTA = 1e-8;

    @Test
    public void testComputeMostLikelySequence() {
        List<Rain> candidates = new ArrayList<>();
        candidates.add(Rain.T);
        candidates.add(Rain.F);

        List<TimeStep<Rain, Umbrella>> timeSteps = new ArrayList<>();
        timeSteps.add( new TimeStep<>(Umbrella.T, candidates) );
        timeSteps.add( new TimeStep<>(Umbrella.T, candidates) );
        timeSteps.add( new TimeStep<>(Umbrella.F, candidates) );
        timeSteps.add( new TimeStep<>(Umbrella.T, candidates) );

        ViterbiAlgorithm<Rain, Umbrella> viterbi = new ViterbiAlgorithm<>();
        MostLikelySequence<Rain, Umbrella> result = viterbi.compute(
                new UmbrellaProbabilities(), timeSteps.iterator(), true);


        // Check most likely sequence
        assertEquals(Arrays.asList(Rain.T, Rain.T, Rain.F, Rain.T), result.sequence);

        // Check for HMM breaks
        assertFalse(result.isBroken);

        // Check message history
        List<Map<Rain, Double>> expectedMessageHistory = new ArrayList<>();
        Map<Rain, Double> message = new HashMap<>();
        message.put(Rain.T, 0.9);
        message.put(Rain.F, 0.2);
        expectedMessageHistory.add(message);

        message = new HashMap<>();
        message.put(Rain.T, 0.567);
        message.put(Rain.F, 0.054);
        expectedMessageHistory.add(message);

        message = new HashMap<>();
        message.put(Rain.T, 0.03969);
        message.put(Rain.F, 0.13608);
        expectedMessageHistory.add(message);

        message = new HashMap<>();
        message.put(Rain.T, 0.0367416);
        message.put(Rain.F, 0.0190512);
        expectedMessageHistory.add(message);

        List<Map<Rain, Double>> actualMessageHistory = result.messageHistory;
        checkMessageHistory(expectedMessageHistory, actualMessageHistory);
    }

    private void checkMessageHistory(List<Map<Rain, Double>> expectedMessageHistory,
            List<Map<Rain, Double>> actualMessageHistory) {
        assertEquals(expectedMessageHistory.size(), actualMessageHistory.size());
        for (int i = 0 ; i < expectedMessageHistory.size() ; i++) {
            checkMessage(expectedMessageHistory.get(i), actualMessageHistory.get(i));
        }
    }

    private void checkMessage(Map<Rain, Double> expectedMessage, Map<Rain, Double> actualMessage) {
        assertEquals(expectedMessage.size(), actualMessage.size());
        for (Map.Entry<Rain, Double> entry : expectedMessage.entrySet()) {
            assertEquals(entry.getValue(), Math.exp(actualMessage.get(entry.getKey())), DELTA);
        }
    }

}
