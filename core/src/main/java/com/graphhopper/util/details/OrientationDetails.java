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

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.Orientation;
import com.graphhopper.util.EdgeIteratorState;

/**
 * Path detail builder for the orientation encoded value. Reports null for edges marked with the
 * {@link Orientation#UNDEFINED} sentinel (e.g. zero-length barrier edges), instead of leaking the
 * raw sentinel value.
 */
public class OrientationDetails extends AbstractPathDetailsBuilder {

    private final DecimalEncodedValue ev;
    private Double value;
    private boolean initialized;

    public OrientationDetails(DecimalEncodedValue ev) {
        super(Orientation.KEY);
        this.ev = ev;
    }

    @Override
    protected Object getCurrentValue() {
        return value;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        double raw = edge.get(ev);
        Double tmpVal = raw >= Orientation.UNDEFINED ? null : raw;
        if (!initialized || !java.util.Objects.equals(tmpVal, value)) {
            value = tmpVal;
            initialized = true;
            return true;
        }
        return false;
    }
}
