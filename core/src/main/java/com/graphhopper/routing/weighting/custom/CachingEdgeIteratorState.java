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

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.EdgeIteratorStateDecorator;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;

class CachingEdgeIteratorState extends EdgeIteratorStateDecorator {

    private PointList geometry;
    private BBox bbox;

    public CachingEdgeIteratorState(EdgeIteratorState edgeState) {
        super(edgeState);
    }

    @Override
    public PointList fetchWayGeometry(FetchMode mode) {
        if (mode != FetchMode.ALL) {
            return delegate.fetchWayGeometry(mode);
        }

        if (geometry == null) {
            geometry = delegate.fetchWayGeometry(FetchMode.ALL).makeImmutable();
        }
        return geometry;
    }

    @Override
    public BBox getBounds() {
        if (bbox == null) {
            bbox = super.getBounds();
        }
        return bbox;
    }
}
