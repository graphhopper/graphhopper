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
package com.graphhopper.util;

import com.graphhopper.routing.ev.*;
import com.graphhopper.search.EdgeKVStorage;
import com.graphhopper.storage.IntsRef;

import java.util.List;

/**
 * Implementation of the {@link EdgeIteratorState} interface which delegates all calls by default.
 */
public abstract class EdgeIteratorStateDecorator implements EdgeIteratorState {

    protected final EdgeIteratorState delegate;

    protected EdgeIteratorStateDecorator(EdgeIteratorState delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getEdge() {
        return delegate.getEdge();
    }

    @Override
    public int getEdgeKey() {
        return delegate.getEdgeKey();
    }

    @Override
    public int getReverseEdgeKey() {
        return delegate.getReverseEdgeKey();
    }

    @Override
    public int getBaseNode() {
        return delegate.getBaseNode();
    }

    @Override
    public int getAdjNode() {
        return delegate.getAdjNode();
    }

    @Override
    public PointList fetchWayGeometry(FetchMode mode) {
        return delegate.fetchWayGeometry(mode);
    }

    @Override
    public EdgeIteratorState setWayGeometry(PointList list) {
        return delegate.setWayGeometry(list);
    }

    @Override
    public double getDistance() {
        return delegate.getDistance();
    }

    @Override
    public EdgeIteratorState setDistance(double dist) {
        return delegate.setDistance(dist);
    }

    @Override
    public IntsRef getFlags() {
        return delegate.getFlags();
    }

    @Override
    public EdgeIteratorState setFlags(IntsRef edgeFlags) {
        return delegate.setFlags(edgeFlags);
    }

    @Override
    public boolean get(BooleanEncodedValue property) {
        return delegate.get(property);
    }

    @Override
    public EdgeIteratorState set(BooleanEncodedValue property, boolean value) {
        return delegate.set(property, value);
    }

    @Override
    public boolean getReverse(BooleanEncodedValue property) {
        return delegate.getReverse(property);
    }

    @Override
    public EdgeIteratorState setReverse(BooleanEncodedValue property, boolean value) {
        return delegate.setReverse(property, value);
    }

    @Override
    public EdgeIteratorState set(BooleanEncodedValue property, boolean fwd, boolean bwd) {
        return delegate.set(property, fwd, bwd);
    }

    @Override
    public int get(IntEncodedValue property) {
        return delegate.get(property);
    }

    @Override
    public EdgeIteratorState set(IntEncodedValue property, int value) {
        return delegate.set(property, value);
    }

    @Override
    public int getReverse(IntEncodedValue property) {
        return delegate.getReverse(property);
    }

    @Override
    public EdgeIteratorState setReverse(IntEncodedValue property, int value) {
        return delegate.setReverse(property, value);
    }

    @Override
    public EdgeIteratorState set(IntEncodedValue property, int fwd, int bwd) {
        return delegate.set(property, fwd, bwd);
    }

    @Override
    public double get(DecimalEncodedValue property) {
        return delegate.get(property);
    }

    @Override
    public EdgeIteratorState set(DecimalEncodedValue property, double value) {
        return delegate.set(property, value);
    }

    @Override
    public double getReverse(DecimalEncodedValue property) {
        return delegate.getReverse(property);
    }

    @Override
    public EdgeIteratorState setReverse(DecimalEncodedValue property, double value) {
        return delegate.setReverse(property, value);
    }

    @Override
    public EdgeIteratorState set(DecimalEncodedValue property, double fwd, double bwd) {
        return delegate.set(property, fwd, bwd);
    }

    @Override
    public <T extends Enum<?>> T get(EnumEncodedValue<T> property) {
        return delegate.get(property);
    }

    @Override
    public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T value) {
        return delegate.set(property, value);
    }

    @Override
    public <T extends Enum<?>> T getReverse(EnumEncodedValue<T> property) {
        return delegate.getReverse(property);
    }

    @Override
    public <T extends Enum<?>> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value) {
        return delegate.setReverse(property, value);
    }

    @Override
    public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T fwd, T bwd) {
        return delegate.set(property, fwd, bwd);
    }

    @Override
    public String get(StringEncodedValue property) {
        return delegate.get(property);
    }

    @Override
    public EdgeIteratorState set(StringEncodedValue property, String value) {
        return delegate.set(property, value);
    }

    @Override
    public String getReverse(StringEncodedValue property) {
        return delegate.getReverse(property);
    }

    @Override
    public EdgeIteratorState setReverse(StringEncodedValue property, String value) {
        return delegate.setReverse(property, value);
    }

    @Override
    public EdgeIteratorState set(StringEncodedValue property, String fwd, String bwd) {
        return delegate.set(property, fwd, bwd);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public EdgeIteratorState setKeyValues(List<EdgeKVStorage.KeyValue> map) {
        return delegate.setKeyValues(map);
    }

    @Override
    public List<EdgeKVStorage.KeyValue> getKeyValues() {
        return delegate.getKeyValues();
    }

    @Override
    public Object getValue(String key) {
        return delegate.getValue(key);
    }

    @Override
    public EdgeIteratorState detach(boolean reverse) {
        return delegate.detach(reverse);
    }

    @Override
    public EdgeIteratorState copyPropertiesFrom(EdgeIteratorState e) {
        return delegate.copyPropertiesFrom(e);
    }
}
