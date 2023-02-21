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
package com.graphhopper.routing.ev;

import com.graphhopper.util.PMap;

public class DefaultEncodedValueFactory implements EncodedValueFactory {

    @Override
    public EncodedValue create(String name, PMap properties) {
        if (Roundabout.KEY.equals(name)) {
            return Roundabout.create();
        } else if (GetOffBike.KEY.equals(name)) {
            return GetOffBike.create();
        } else if (RoadClass.KEY.equals(name)) {
            return new EnumEncodedValue<>(RoadClass.KEY, RoadClass.class);
        } else if (RoadClassLink.KEY.equals(name)) {
            return new SimpleBooleanEncodedValue(RoadClassLink.KEY);
        } else if (RoadEnvironment.KEY.equals(name)) {
            return new EnumEncodedValue<>(RoadEnvironment.KEY, RoadEnvironment.class);
        } else if (RoadAccess.KEY.equals(name)) {
            return new EnumEncodedValue<>(RoadAccess.KEY, RoadAccess.class);
        } else if (MaxSpeed.KEY.equals(name)) {
            return MaxSpeed.create();
        } else if (MaxWeight.KEY.equals(name)) {
            return MaxWeight.create();
        } else if (MaxHeight.KEY.equals(name)) {
            return MaxHeight.create();
        } else if (MaxWidth.KEY.equals(name)) {
            return MaxWidth.create();
        } else if (MaxAxleLoad.KEY.equals(name)) {
            return MaxAxleLoad.create();
        } else if (MaxLength.KEY.equals(name)) {
            return MaxLength.create();
        } else if (Hgv.KEY.equals(name)) {
            return new EnumEncodedValue<>(Hgv.KEY, Hgv.class);
        } else if (Surface.KEY.equals(name)) {
            return new EnumEncodedValue<>(Surface.KEY, Surface.class);
        } else if (Smoothness.KEY.equals(name)) {
            return new EnumEncodedValue<>(Smoothness.KEY, Smoothness.class);
        } else if (Toll.KEY.equals(name)) {
            return new EnumEncodedValue<>(Toll.KEY, Toll.class);
        } else if (TrackType.KEY.equals(name)) {
            return new EnumEncodedValue<>(TrackType.KEY, TrackType.class);
        } else if (BikeNetwork.KEY.equals(name) || FootNetwork.KEY.equals(name)) {
            return new EnumEncodedValue<>(name, RouteNetwork.class);
        } else if (Hazmat.KEY.equals(name)) {
            return new EnumEncodedValue<>(Hazmat.KEY, Hazmat.class);
        } else if (HazmatTunnel.KEY.equals(name)) {
            return new EnumEncodedValue<>(HazmatTunnel.KEY, HazmatTunnel.class);
        } else if (HazmatWater.KEY.equals(name)) {
            return new EnumEncodedValue<>(HazmatWater.KEY, HazmatWater.class);
        } else if (Lanes.KEY.equals(name)) {
            return Lanes.create();
        } else if (Footway.KEY.equals(name)) {
            return new EnumEncodedValue<>(Footway.KEY, Footway.class);
        } else if (OSMWayID.KEY.equals(name)) {
            return OSMWayID.create();
        } else if (MtbRating.KEY.equals(name)) {
            return MtbRating.create();
        } else if (HikeRating.KEY.equals(name)) {
            return HikeRating.create();
        } else if (HorseRating.KEY.equals(name)) {
            return HorseRating.create();
        } else if (Country.KEY.equals(name)) {
            return Country.create();
        } else if (name.endsWith(Subnetwork.key(""))) {
            return new SimpleBooleanEncodedValue(name);
        } else if (MaxSlope.KEY.equals(name)) {
            return MaxSlope.create();
        } else if (AverageSlope.KEY.equals(name)) {
            return AverageSlope.create();
        } else if (Curvature.KEY.equals(name)) {
            return Curvature.create();
        } else
            throw new IllegalArgumentException("DefaultEncodedValueFactory cannot find EncodedValue " + name);
    }
}
