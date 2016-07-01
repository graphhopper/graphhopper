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

/**
 * This interface needs to be implemented and passed to {@link Hmm} to specify observation
 * and transition probabilities. These probabilities can be made time step dependent by storing a
 * time step in each state.
 *
 * The methods return logarithmic (ln) probabilities instead of plain probabilities to avoid
 * arithmetic underflows for very small probabilities.
 *
 * @param <S> state class/interface
 * @param <O> observation class/interface
 */
public interface HmmProbabilities<S, O> {

    /**
     * Returns the logarithmic probability or the logarithmic probability density of making the
     * specified observation in the specified state, i.e. p(observation|state).
     */
    double emissionLogProbability(S state, O observation);

    /**
     * Returns the logarithmic probability or the logarithmic probability density of the transition
     * from sourceState to targetState, i.e. p(targetState|sourceState).
     */
    double transitionLogProbability(S sourceState, O sourceObservarion, S targetState, O targetObservation);

}
