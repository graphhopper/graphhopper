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

package com.bmw.hmm;

import java.util.Objects;

/**
 * State of the most likely sequence with additional information.
 *
 * @param <S> the state type
 * @param <O> the observation type
 * @param <D> the transition descriptor type
 */
public class SequenceState<S, O, D> {

    public final S state;

    /**
     * Null if HMM was started with initial state probabilities and state is the initial state.
     */
    public final O observation;

    /**
     * Null if transition descriptor was not provided.
     */
    public final D transitionDescriptor;

    public SequenceState(S state, O observation, D transitionDescriptor) {
        this.state = state;
        this.observation = observation;
        this.transitionDescriptor = transitionDescriptor;
    }

    @Override
    public String toString() {
        return "SequenceState [state=" + state + ", observation=" + observation
                + ", transitionDescriptor=" + transitionDescriptor
                + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, observation, transitionDescriptor);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SequenceState<?, ?, ?> other = (SequenceState<?, ?, ?>) obj;
        return Objects.equals(state, other.state) &&
                Objects.equals(observation, other.observation) &&
                Objects.equals(transitionDescriptor, other.transitionDescriptor);
    }

}
