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

package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.*;

import java.util.*;

public class EVCollection implements EncodedValueLookup {
    public static final String SPECIAL_SEPARATOR = "$";
    private final Map<String, EncodedValue> encodedValueMap = new LinkedHashMap<>();
    private final EncodedValue.InitializerConfig edgeConfig = new EncodedValue.InitializerConfig();
    private final EncodedValue.InitializerConfig turnCostConfig = new EncodedValue.InitializerConfig();

    public void addEncodedValue(EncodedValue ev, boolean withNamespace) {
        // todonow: get rid of withnamespace? does this mean flagencoders currently can **only** create with namespace and tagparsers only without?
        // todonow: why normalize?
        // todonow: intellij hint (suspicious regex expression)
        String normalizedKey = ev.getName().replaceAll(SPECIAL_SEPARATOR, "_");
        if (hasEncodedValue(normalizedKey))
            throw new IllegalStateException("EncodedValue " + ev.getName() + " collides with " + normalizedKey);
        if (!withNamespace && !isSharedEncodedValue(ev))
            throw new IllegalArgumentException("EncodedValue " + ev.getName() + " must not contain namespace character '" + SPECIAL_SEPARATOR + "'");
        if (withNamespace && isSharedEncodedValue(ev))
            throw new IllegalArgumentException("EncodedValue " + ev.getName() + " must contain namespace character '" + SPECIAL_SEPARATOR + "'");
        ev.init(edgeConfig);
        encodedValueMap.put(ev.getName(), ev);
    }

    public void addTurnCostEncodedValue(EncodedValue ev) {
        // todonow: what is turnCostConfig used or needed for?
        ev.init(turnCostConfig);
        // todonow: use hasEncodedValue?
        if (encodedValueMap.containsKey(ev.getName()))
            throw new IllegalArgumentException("Already defined: " + ev.getName() + ". Please note that " +
                    "EncodedValues for edges and turn cost are in the same namespace.");
        encodedValueMap.put(ev.getName(), ev);
    }

    public boolean hasEncodedValue(String key) {
        // todonow: use containsKey
        return encodedValueMap.get(key) != null;
    }

    public List<EncodedValue> getEncodedValues() {
        return Collections.unmodifiableList(new ArrayList<>(encodedValueMap.values()));
    }

    @Override
    public BooleanEncodedValue getBooleanEncodedValue(String key) {
        return getEncodedValue(key, BooleanEncodedValue.class);
    }

    @Override
    public IntEncodedValue getIntEncodedValue(String key) {
        return getEncodedValue(key, IntEncodedValue.class);
    }

    @Override
    public DecimalEncodedValue getDecimalEncodedValue(String key) {
        return getEncodedValue(key, DecimalEncodedValue.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Enum<?>> EnumEncodedValue<T> getEnumEncodedValue(String key, Class<T> type) {
        return getEncodedValue(key, EnumEncodedValue.class);
    }

    @Override
    public StringEncodedValue getStringEncodedValue(String key) {
        return getEncodedValue(key, StringEncodedValue.class);
    }

    @Override
    public <T extends EncodedValue> T getEncodedValue(String key, Class<T> encodedValueType) {
        EncodedValue ev = encodedValueMap.get(key);
        if (ev == null)
            throw new IllegalArgumentException("Cannot find EncodedValue " + key + " in collection: " + ev);
        return (T) ev;
    }

    public int getIntsForFlags() {
        return (int) Math.ceil((double) edgeConfig.getRequiredBits() / 32.0);
    }

    public boolean isEmpty() {
        return encodedValueMap.isEmpty();
    }

    public String getSharedEncodedValuesString() {
        StringBuilder str = new StringBuilder();
        for (EncodedValue ev : encodedValueMap.values()) {
            if (!isSharedEncodedValue(ev))
                continue;

            if (str.length() > 0)
                str.append(",");

            str.append(ev.toString());
        }

        return str.toString();
    }

    private static boolean isSharedEncodedValue(EncodedValue ev) {
        // todonow: get rid of concept of shared/non-shared EVs once FlagEncoders are 'gone'?
        return EncodingManager.isValidEncodedValue(ev.getName()) && !ev.getName().contains(SPECIAL_SEPARATOR);
    }

    @Override
    public boolean equals(Object o) {
        // todonow: why not just use default identity (here and for EncodingManager)?
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EVCollection that = (EVCollection) o;
        return encodedValueMap.equals(that.encodedValueMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(encodedValueMap);
    }

}
