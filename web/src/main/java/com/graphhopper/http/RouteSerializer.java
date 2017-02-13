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
package com.graphhopper.http;

import com.graphhopper.GHResponse;
import com.graphhopper.util.PointList;

import java.util.Map;

/**
 * This interface specifies how the route should be transformed into JSON.
 * <p>
 *
 * @author Peter Karich
 */
public interface RouteSerializer {
    /**
     * This method transforms the specified response into a JSON.
     */
    Map<String, Object> toJSON(GHResponse response,
                               boolean calcPoints, boolean pointsEncoded,
                               boolean includeElevation, boolean enableInstructions);

    /**
     * This method returns either a Map containing the GeoJSON of the specified points OR the string
     * encoded polyline of it.
     */
    Object createPoints(PointList points, boolean pointsEncoded, boolean includeElevation);
}
