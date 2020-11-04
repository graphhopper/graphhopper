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

import java.util.Collections;
import java.util.List;

import com.graphhopper.routing.util.TransportationMode;
import org.locationtech.jts.geom.Polygon;

import com.graphhopper.routing.ev.RoadAccess;
import com.graphhopper.routing.ev.RoadClass;

/**
 * @author Robin Boldt
 */
public abstract class AbstractSpatialRule implements SpatialRule {
    
    public static final int DEFAULT_PRIORITY = 100;

    private final List<Polygon> borders;
    
    public AbstractSpatialRule(List<Polygon> borders) {
        this.borders = borders;
    }
    
    public AbstractSpatialRule(Polygon border) {
        this(Collections.singletonList(border));
    }
    
    @Override
    public double getMaxSpeed(RoadClass roadClass, TransportationMode transport, double currentMaxSpeed) {
        return currentMaxSpeed;
    }

    @Override
    public RoadAccess getAccess(RoadClass roadClass, TransportationMode transport, RoadAccess currentRoadAccess) {
        return currentRoadAccess;
    }
    
    public List<Polygon> getBorders() {
        return borders;
    }

    @Override
    public int getPriority() {
        return DEFAULT_PRIORITY;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SpatialRule)) {
            return false;
        }
        return this.getId().equals(((SpatialRule) obj).getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SpatialRule [getId()=");
        builder.append(getId());
        builder.append(", getPriority()=");
        builder.append(getPriority());
        builder.append("]");
        return builder.toString();
    }
}
