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
package com.graphhopper.core.util.details;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.core.util.EdgeIteratorState;

public class DecimalDetails extends AbstractPathDetailsBuilder {

    private final DecimalEncodedValue ev;
    private Double decimalValue;
    private final String infinityJsonValue;
    private final double precision;

    public DecimalDetails(String name, DecimalEncodedValue ev) {
        this(name, ev, null, 0.001);
    }

    /**
     * @param infinityJsonValue DecimalEncodedValue can return infinity as default value, but JSON cannot include this
     *                          https://stackoverflow.com/a/9218955/194609 so we need a special string to handle this or null.
     * @param precision         e.g. 0.1 to avoid creating too many path details, i.e. round the speed to the specified precision
     *                          *                  before detecting a change.
     */
    public DecimalDetails(String name, DecimalEncodedValue ev, String infinityJsonValue, double precision) {
        super(name);
        this.ev = ev;
        this.infinityJsonValue = infinityJsonValue;
        this.precision = precision;
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
        if (decimalValue == null || Math.abs(tmpVal - decimalValue) >= precision) {
            this.decimalValue = Double.isInfinite(tmpVal) ? tmpVal : Math.round(tmpVal / precision) * precision;
            return true;
        }
        return false;
    }
}
