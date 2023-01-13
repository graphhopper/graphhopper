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

import com.graphhopper.routing.ev.StringEncodedValue;
import com.graphhopper.core.util.EdgeIteratorState;

public class StringDetails extends AbstractPathDetailsBuilder {

    private final StringEncodedValue ev;
    private String currentVal;

    public StringDetails(String name, StringEncodedValue ev) {
        super(name);
        this.ev = ev;
    }

    @Override
    protected Object getCurrentValue() {
        return currentVal;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        String val = edge.get(ev);
        // we can use the reference equality here
        if (!val.equals(currentVal)) {
            this.currentVal = val;
            return true;
        }
        return false;
    }
}
