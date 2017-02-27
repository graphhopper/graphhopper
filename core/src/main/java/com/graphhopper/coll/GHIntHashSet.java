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
package com.graphhopper.coll;

import com.carrotsearch.hppc.HashOrderMixingStrategy;
import com.carrotsearch.hppc.IntContainer;
import com.carrotsearch.hppc.IntHashSet;

import static com.graphhopper.coll.GHIntObjectHashMap.DETERMINISTIC;

/**
 * Prefer GHTBitSet or GHBitSetImpl over this class.
 *
 * @author Peter Karich
 */
public class GHIntHashSet extends IntHashSet {

    public GHIntHashSet() {
        super(10, 0.75, DETERMINISTIC);
    }

    public GHIntHashSet(int capacity) {
        super(capacity, 0.75, DETERMINISTIC);
    }

    public GHIntHashSet(int capacity, double loadFactor) {
        super(capacity, loadFactor, DETERMINISTIC);
    }

    public GHIntHashSet(int capacity, double loadFactor, HashOrderMixingStrategy hashOrderMixer) {
        super(capacity, loadFactor, hashOrderMixer);
    }

    public GHIntHashSet(IntContainer container) {
        this(container.size());
        addAll(container);
    }

    public static IntHashSet from(int... elements) {
        final GHIntHashSet set = new GHIntHashSet(elements.length);
        set.addAll(elements);
        return set;
    }
}
