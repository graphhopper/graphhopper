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

import com.graphhopper.coll.MapEntry;
import com.graphhopper.util.EdgeIteratorState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simply returns the same value everywhere, useful to represent values that are the same between two (via-)points
 */
public class ConstantDetailsBuilder extends AbstractPathDetailsBuilder {
    private final Object value;
    private boolean firstEdge = true;

    public ConstantDetailsBuilder(String name, Object value) {
        super(name);
        this.value = value;
    }

    @Override
    protected Object getCurrentValue() {
        return value;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        if (firstEdge) {
            firstEdge = false;
            return true;
        } else
            return false;
    }

    @Override
    public Map.Entry<String, List<PathDetail>> build() {
        if (firstEdge)
            // #2915 if there was no edge at all we need to add a single entry manually here
            return new MapEntry<>(getName(), new ArrayList<>(List.of(new PathDetail(value))));
        return super.build();
    }
}
