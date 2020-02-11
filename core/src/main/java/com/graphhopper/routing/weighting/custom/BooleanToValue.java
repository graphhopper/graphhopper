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
package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.util.EdgeIteratorState;

public final class BooleanToValue implements ConfigMapEntry {
    private final BooleanEncodedValue bev;
    private final double value, elseValue;

    public BooleanToValue(BooleanEncodedValue bev, double value, double elseValue) {
        this.bev = bev;
        this.value = value;
        this.elseValue = elseValue;
    }

    @Override
    public double getValue(EdgeIteratorState iter, boolean reverse) {
        return iter.get(bev) ? value : elseValue;
    }

    @Override
    public String toString() {
        return bev.getName() + ": " + value + ", else:" + elseValue;
    }
}
