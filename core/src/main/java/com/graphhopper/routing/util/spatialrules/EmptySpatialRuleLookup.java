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
package com.graphhopper.routing.util.spatialrules;


import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.shapes.GHPoint;

import java.util.List;

/**
 * Allways returns the empty rule
 *
 * @author Robin Boldt
 */
public class EmptySpatialRuleLookup extends AbstractSpatialRuleLookup {

    @Override
    public SpatialRule lookupRule(double lat, double lon) {
        return EMPTY_RULE;
    }

    @Override
    public SpatialRule lookupRule(GHPoint point) {
        return EMPTY_RULE;
    }

    @Override
    public void addRule(SpatialRule rule) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void visualize(int i) {

    }
}
