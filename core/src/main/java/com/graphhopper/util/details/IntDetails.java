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

import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.util.EdgeIteratorState;

public class IntDetails extends AbstractPathDetailsBuilder {

    private final IntEncodedValue ev;
    private final boolean returnMinus;
    private int intVal = -1;

    /**
     * @param returnMinus true if getCurrentValue should return -1 for the default value (e.g. if max_speed not mapped returning 0 would be confusing)
     */
    public IntDetails(String name, IntEncodedValue ev, boolean returnMinus) {
        super(name);
        this.ev = ev;
        this.returnMinus = returnMinus;
    }

    @Override
    protected Object getCurrentValue() {
        // the problem is e.g. max_speed
        if (returnMinus && intVal == 0)
            return -1;
        return intVal;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        int val = edge.get(ev);
        if (val != intVal) {
            this.intVal = val;
            return true;
        }
        return false;
    }
}
