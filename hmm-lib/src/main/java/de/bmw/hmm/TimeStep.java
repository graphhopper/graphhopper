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

import java.util.Arrays;
import java.util.Collection;

/**
 * Contains the observation and the state candidates of a particular time step.
 *
 * @param <S> state class/interface
 * @param <O> observation class/interface
 */
public class TimeStep<S, O> {

    /**
     * Observation made at this time step.
     */
    public final O observation;

    /**
     * State candidates at this time step.
     */
    public final Collection<S> candidates;

    public TimeStep(O observation, Collection<S> candidates) {
        if (observation == null || candidates == null) {
            throw new NullPointerException();
        }

        this.observation = observation;
        this.candidates = candidates;
    }

    @SafeVarargs
    public TimeStep(O observation, S... candidates) {
        this(observation, Arrays.asList(candidates));
    }
}
