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
package com.graphhopper.routing.profiles;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.Helper;

public class DefaultEncodedValueFactory implements EncodedValueFactory {
    @Override
    public EncodedValue create(String string) {
        if (Helper.isEmpty(string))
            throw new IllegalArgumentException("No string provided to load EncodedValue");

        final EncodedValue enc;
        String name = string.split("\\|")[0];
        if (name.isEmpty())
            throw new IllegalArgumentException("To load EncodedValue a name is required. " + string);

        // creating the Country EV is done while SpatialRuleIndex is created and not here
        if (Roundabout.KEY.equals(name)) {
            enc = Roundabout.create();
        } else if (RoadClass.KEY.equals(name)) {
            enc = new EnumEncodedValue<>(RoadClass.KEY, RoadClass.class);
        } else if (RoadClassLink.KEY.equals(name)) {
            enc = new SimpleBooleanEncodedValue(RoadClassLink.KEY);
        } else if (RoadEnvironment.KEY.equals(name)) {
            enc = new EnumEncodedValue<>(RoadEnvironment.KEY, RoadEnvironment.class);
        } else if (RoadAccess.KEY.equals(name)) {
            enc = new EnumEncodedValue<>(RoadAccess.KEY, RoadAccess.class);
        } else if (MaxSpeed.KEY.equals(name)) {
            enc = MaxSpeed.create();
        } else if (MaxWeight.KEY.equals(name)) {
            enc = MaxWeight.create();
        } else if (MaxHeight.KEY.equals(name)) {
            enc = MaxHeight.create();
        } else if (MaxWidth.KEY.equals(name)) {
            enc = MaxWidth.create();
        } else if (MaxAxleLoad.KEY.equals(name)) {
            enc = MaxAxleLoad.create();
        } else if (MaxLength.KEY.equals(name)) {
            enc = MaxLength.create();
        } else if (Surface.KEY.equals(name)) {
            enc = new EnumEncodedValue<>(Surface.KEY, Surface.class);
        } else if (Toll.KEY.equals(name)) {
            enc = new EnumEncodedValue<>(Toll.KEY, Toll.class);
        } else if (TrackType.KEY.equals(name)) {
            enc = new EnumEncodedValue<>(TrackType.KEY, TrackType.class);
        } else if (name.endsWith(EncodingManager.getKey("", RouteNetwork.EV_SUFFIX))) {
            enc = new EnumEncodedValue<>(EncodingManager.getKey(name, RouteNetwork.EV_SUFFIX), RouteNetwork.class);
        } else if (Hazmat.KEY.equals(name)) {
            enc = new EnumEncodedValue<>(Hazmat.KEY, Hazmat.class);
        } else if (HazmatTunnel.KEY.equals(name)) {
            enc = new EnumEncodedValue<>(HazmatTunnel.KEY, HazmatTunnel.class);
        } else if (HazmatWater.KEY.equals(name)) {
            enc = new EnumEncodedValue<>(HazmatWater.KEY, HazmatWater.class);
        } else {
            throw new IllegalArgumentException("DefaultEncodedValueFactory cannot find EncodedValue " + name);
        }
        return enc;
    }
}
