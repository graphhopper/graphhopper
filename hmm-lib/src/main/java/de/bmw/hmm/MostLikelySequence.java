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

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Contains the most likely sequence and additional results of the Viterbi algorithm.
 */
public class MostLikelySequence<S, O> {
    public final List<S> sequence;

    /**
     * Returns whether an HMM break occurred.
     *
     * @see Hmm#computeMostLikelySequence(HmmProbabilities, Iterator)
     */
    public final boolean isBroken;

    /**
     *  Sequence of computed messages for each time step. Is null if message history
     *  is not kept (see compute()).
     *
     *  For each state s_t of the time step t, messageHistory.get(t).get(s_t) contains the log
     *  probability of the most likely sequence ending in state s_t with given observations
     *  o_1, ..., o_t.
     *  Formally, this is max log p(s_1, ..., s_t, o_1, ..., o_t) w.r.t. s_1, ..., s_{t-1}.
     *  Note that to compute the most likely state sequence, it is sufficient and more
     *  efficient to compute in each time step the joint probability of states and observations
     *  instead of computing the conditional probability of states given the observations.
     */
    public final List<Map<S, Double>> messageHistory;

    /**
     * backPointerSequence.get(t).get(s) contains the previous state (at time t-1) of the most
     * likely state sequence passing at time step t through state s.
     * Since there are no previous states for t=1, backPointerSequence starts with t=2.
     */
    public final List<Map<S, S>> backPointerSequence;

    public MostLikelySequence(List<S> mostLikelySequence, boolean isBroken,
            List<Map<S, S>> backPointerSequence, List<Map<S, Double>> messageHistory) {
        this.sequence = mostLikelySequence;
        this.isBroken = isBroken;
        this.messageHistory = messageHistory;
        this.backPointerSequence = backPointerSequence;
    }

    public String messageHistoryString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Message history with log probabilies\n\n");
        int i = 0;
        for (Map<S, Double> message : messageHistory) {
            sb.append("Time step " + i + "\n");
            i++;
            for (S state : message.keySet()) {
                sb.append(state + ": " + message.get(state) + "\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}