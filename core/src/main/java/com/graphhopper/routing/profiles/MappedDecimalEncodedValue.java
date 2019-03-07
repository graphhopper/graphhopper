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
package com.graphhopper.routing.profiles;

import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.IntIntHashMap;
import com.graphhopper.storage.IntsRef;

import java.util.Collection;

/**
 * For certain values it can be more efficient to store the mapping instead of the value and a factor. E.g. for
 * "maximum height of a road" there might be only a few distinct values where we need full precision.
 *
 * @see FactorizedDecimalEncodedValue
 */
public final class MappedDecimalEncodedValue extends SimpleIntEncodedValue implements DecimalEncodedValue {
    private final int toValueMap[];
    private final IntIntHashMap toStorageMap;
    private final double precision;

    /**
     * This class allows to store a value efficiently if it can take only a few decimal values that are not necessarily
     * consecutive.
     */
    public MappedDecimalEncodedValue(String name, Collection<Double> values, double precision, boolean storeBothDirections) {
        super(name, 32 - Integer.numberOfLeadingZeros(values.size()), storeBothDirections);

        this.precision = precision;
        // store int-int mapping
        toValueMap = new int[values.size()];
        toStorageMap = new IntIntHashMap(values.size());

        int index = 0;
        IntHashSet dupCheck = new IntHashSet();
        for (double val : values) {
            int intVal = toInt(val);
            if (!dupCheck.add(intVal)) {
                throw new IllegalArgumentException("The value " + val + " was converted to " + intVal + " but this already exists. Remove it to improve efficiency.");
            }
            toValueMap[index] = intVal;
            toStorageMap.put(intVal, index);
            index++;
        }
    }

    private int toInt(double val) {
        return (int) Math.round(val / precision);
    }

    @Override
    public final void setDecimal(boolean reverse, IntsRef ref, double value) {
        int storageInt = toStorageMap.getOrDefault(toInt(value), -1);
        if (storageInt < 0)
            throw new IllegalArgumentException("Cannot find value " + value + " (" + toInt(value) + ") in map to store it");

        super.setInt(reverse, ref, storageInt);
    }

    @Override
    public final double getDecimal(boolean reverse, IntsRef ref) {
        int value = getInt(reverse, ref);
        return toValueMap[value] * precision;
    }
}
