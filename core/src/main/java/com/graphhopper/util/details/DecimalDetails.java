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
package com.graphhopper.util.details;

import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.util.EdgeIteratorState;

public class DecimalDetails extends AbstractPathDetailsBuilder {

    private final DecimalEncodedValue ev;
    private double decimalValue = -1;
    private final String infinityJsonValue;

    public DecimalDetails(String name, DecimalEncodedValue ev) {
        this(name, ev, null);
    }

    /**
     * DecimalEncodedValue can return infinity as default value, but JSON cannot include this
     * https://stackoverflow.com/a/9218955/194609
     */
    public DecimalDetails(String name, DecimalEncodedValue ev, String infinityJsonValue) {
        super(name);
        this.ev = ev;
        this.infinityJsonValue = infinityJsonValue;
    }

    @Override
    protected Object getCurrentValue() {
        if (Double.isInfinite(decimalValue))
            return infinityJsonValue;

        return decimalValue;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        double tmpVal = edge.get(ev);
        if (Math.abs(tmpVal - decimalValue) > 0.0001) {
            this.decimalValue = tmpVal;
            return true;
        }
        return false;
    }
}
