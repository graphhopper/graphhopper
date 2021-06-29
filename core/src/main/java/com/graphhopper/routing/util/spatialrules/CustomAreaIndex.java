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

import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.List;
import java.util.stream.Collectors;

public class CustomAreaIndex {
    private final GeometryFactory gf;
    private final STRtree index;

    public CustomAreaIndex(List<CustomArea> customAreas) {
        gf = new GeometryFactory();
        index = new STRtree();
        for (CustomArea customArea : customAreas) {
            for (Polygon border : customArea.getBorders()) {
                Envelope borderEnvelope = border.getEnvelopeInternal();
                index.insert(borderEnvelope, customArea);
            }
        }
        index.build();
    }

    public List<CustomArea> query(double lat, double lon) {
        Envelope searchEnv = new Envelope(lon, lon, lat, lat);
        Point point = gf.createPoint(new Coordinate(lon, lat));
        @SuppressWarnings("unchecked")
        List<CustomArea> result = index.query(searchEnv);
        return result.stream().filter(
                c -> c.getBorders().stream()
                        // todo: SpatialRuleLookupJTS uses SpatialRuleContainer#covers, but why?
                        .anyMatch(point::within)).collect(Collectors.toList());
    }

}


