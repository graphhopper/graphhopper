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
package com.graphhopper.navigation;

import com.graphhopper.util.Helper;

// NavigationTransportMode is not TransportationMode; its primary purpose is to define the transport mode for navigation instructions
public enum NavigationTransportMode {
    CAR(),
    BIKE(),
    FOOT();

    public static NavigationTransportMode find(String name) {
        switch (name) {
            case "walking":
            case "walk":
            case "hiking":
            case "hike":
            case "foot":
            case "pedestrian":
                return FOOT;
            case "cycling":
            case "cyclist":
            case "mtb":
            case "racingbike":
            case "bike":
                return BIKE;
            default:
                return CAR;
        }
    }
}
