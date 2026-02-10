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

import java.util.function.LongUnaryOperator;

/**
 * @author Peter Karich
 */
public interface LongLongMap {
    long put(long key, long value);

    /**
     * If the key is absent, inserts valueIfAbsent.
     * If the key is present, updates it with computeIfPresent.applyAsLong(currentValue).
     * This is done in a single traversal.
     *
     * @return the previous value, or the empty value if the key was absent
     */
    long putOrCompute(long key, long valueIfAbsent, LongUnaryOperator computeIfPresent);

    long get(long key);

    long getSize();

    long getMaxValue();

    void optimize();

    int getMemoryUsage();

    void clear();
}
