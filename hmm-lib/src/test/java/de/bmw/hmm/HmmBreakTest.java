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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;


class TestState {
    public int timeStep;
    public int stateVal;

    /**
     *
     * @param timeStep should start with 1 since the HMM does not use initial state probabilities.
     * @param stateVal Used to represent the actual state.
     */
    public TestState(int timeStep, int stateVal) {
        this.timeStep = timeStep;
        this.stateVal = stateVal;
    }

    @Override
    public String toString() {
        return "State [timeStep=" + timeStep + ", stateVal=" + stateVal + "]";
    }
}

class TestObservation {
    /**
     * This observation is made at each time step.
     */
    public final static TestObservation O = new TestObservation();
}

class BreakingHmmProbabilities implements HmmProbabilities<TestState, TestObservation> {

    private final int breakAtTimeStep;

    /**
     * If breakAtTimeStep > 0 then breaks at the transition from breakAtTimeStep to
     * breakAtTimeStep + 1.
     * If breakAtTimeStep = 0 then causes all entries of the initial message to be 0.
     * If breakAtTimeStep < 0 then the HMM does not break.
     */
    public BreakingHmmProbabilities(int breakAtTimeStep) {
        this.breakAtTimeStep = breakAtTimeStep;
    }

    @Override
    public double emissionLogProbability(TestState state, TestObservation observation) {
        if (breakAtTimeStep == 0) {
            return log(0.0);
        }

        if (state.stateVal == 1) {
            return log(0.6);
        } else {
            return log(0.4);
        }
    }

    @Override
    public double transitionLogProbability(TestState sourceState, TestObservation testObservation, TestState targetState, TestObservation targetObservation) {
        if (sourceState.timeStep == breakAtTimeStep) {
            return log(0.0);
        }

        if (sourceState.stateVal == targetState.stateVal) {
            return log(0.7);
        } else {
            return log(0.3);
        }
    }
}

public class HmmBreakTest {

    @Test
    public void testEmptySequence() {
        List<TimeStep<TestState, TestObservation>> timeSteps = new ArrayList<>();

        ViterbiAlgorithm<TestState, TestObservation> viterbi = new ViterbiAlgorithm<>();
        MostLikelySequence<TestState, TestObservation> result =
                viterbi.compute(new BreakingHmmProbabilities(-1), timeSteps.iterator(), false);

        assertEquals(Arrays.asList(), result.sequence);
        assertFalse(result.isBroken);
    }

    @Test
    public void testBreakAtInitialMessage() {
        // Test with non-empty message
        List<TimeStep<TestState, TestObservation>> timeSteps = new ArrayList<>();

        List<TestState> candidates = new ArrayList<>();
        TestState s11 = new TestState(1, 1);
        candidates.add(s11);
        TestState s12 = new TestState(1, 2);
        candidates.add(s12);
        timeSteps.add( new TimeStep<>(TestObservation.O, candidates) );

        candidates = new ArrayList<>();
        TestState s21 = new TestState(2, 1);
        candidates.add(s21);
        TestState s22 = new TestState(2, 2);
        candidates.add(s22);
        timeSteps.add( new TimeStep<>(TestObservation.O, candidates) );

        ViterbiAlgorithm<TestState, TestObservation> viterbi = new ViterbiAlgorithm<>();
        MostLikelySequence<TestState, TestObservation> result =
                viterbi.compute(new BreakingHmmProbabilities(0), timeSteps.iterator(), false);

        assertEquals(Arrays.asList(), result.sequence);
        assertTrue(result.isBroken);

        // Test with empty message
        timeSteps.set(0, new TimeStep<TestState, TestObservation>(TestObservation.O));
        result = viterbi.compute(new BreakingHmmProbabilities(-1), timeSteps.iterator(), false);
        assertEquals(Arrays.asList(), result.sequence);
        assertTrue(result.isBroken);
    }

    @Test
    public void testBreakAtFirstTransition() {
        // Test with non-empty message
        List<TimeStep<TestState, TestObservation>> timeSteps = new ArrayList<>();

        List<TestState> candidates = new ArrayList<>();
        TestState s11 = new TestState(1, 1);
        candidates.add(s11);
        TestState s12 = new TestState(1, 2);
        candidates.add(s12);
        timeSteps.add( new TimeStep<>(TestObservation.O, candidates) );

        candidates = new ArrayList<>();
        TestState s21 = new TestState(2, 1);
        candidates.add(s21);
        TestState s22 = new TestState(2, 2);
        candidates.add(s22);
        timeSteps.add( new TimeStep<>(TestObservation.O, candidates) );

        ViterbiAlgorithm<TestState, TestObservation> viterbi = new ViterbiAlgorithm<>();
        MostLikelySequence<TestState, TestObservation> result =
                viterbi.compute(new BreakingHmmProbabilities(1), timeSteps.iterator(), false);

        assertEquals(Arrays.asList(s11), result.sequence);
        assertTrue(result.isBroken);

        // Test with empty message
        timeSteps.set(1, new TimeStep<TestState, TestObservation>(TestObservation.O));
        result = viterbi.compute(new BreakingHmmProbabilities(-1), timeSteps.iterator(), false);
        assertEquals(Arrays.asList(s11), result.sequence);
        assertTrue(result.isBroken);
    }


    @Test
    public void testBreakAtMiddle() {
        // Test with non-empty message
        List<TimeStep<TestState, TestObservation>> timeSteps = new ArrayList<>();

        List<TestState> candidates = new ArrayList<>();
        TestState s11 = new TestState(1, 1);
        candidates.add(s11);
        TestState s12 = new TestState(1, 2);
        candidates.add(s12);
        timeSteps.add( new TimeStep<>(TestObservation.O, candidates) );

        candidates = new ArrayList<>();
        TestState s21 = new TestState(2, 1);
        candidates.add(s21);
        TestState s22 = new TestState(2, 2);
        candidates.add(s22);
        timeSteps.add( new TimeStep<>(TestObservation.O, candidates) );

        candidates = new ArrayList<>();
        TestState s31 = new TestState(3, 1);
        candidates.add(s31);
        TestState s32 = new TestState(3, 2);
        candidates.add(s32);
        timeSteps.add( new TimeStep<>(TestObservation.O, candidates) );

        candidates = new ArrayList<>();
        TestState s41 = new TestState(4, 1);
        candidates.add(s41);
        TestState s42 = new TestState(4, 2);
        candidates.add(s42);
        timeSteps.add( new TimeStep<>(TestObservation.O, candidates) );

        ViterbiAlgorithm<TestState, TestObservation> viterbi = new ViterbiAlgorithm<>();
        MostLikelySequence<TestState, TestObservation> result =
                viterbi.compute(new BreakingHmmProbabilities(2), timeSteps.iterator(), false);

        assertEquals(Arrays.asList(s11, s21), result.sequence);
        assertTrue(result.isBroken);

        // Test with empty message
        timeSteps.set(2, new TimeStep<TestState, TestObservation>(TestObservation.O));
        result = viterbi.compute(new BreakingHmmProbabilities(-1), timeSteps.iterator(), false);
        assertEquals(Arrays.asList(s11, s21), result.sequence);
        assertTrue(result.isBroken);
    }

}
