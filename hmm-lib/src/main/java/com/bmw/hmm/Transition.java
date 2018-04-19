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
 * Represents the transition between two consecutive candidates.
 *
 * @param <S> the state type
 */
public class Transition<S> {
    public final S fromCandidate;
    public final S toCandidate;

    public Transition(S fromCandidate, S toCandidate) {
        this.fromCandidate = fromCandidate;
        this.toCandidate = toCandidate;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromCandidate, toCandidate);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        @SuppressWarnings("unchecked")
        Transition<S> other = (Transition<S>) obj;
        return Objects.equals(fromCandidate, other.fromCandidate) && Objects.equals(toCandidate,
                other.toCandidate);
    }

    @Override
    public String toString() {
        return "Transition [fromCandidate=" + fromCandidate + ", toCandidate="
                + toCandidate + "]";
    }


}
