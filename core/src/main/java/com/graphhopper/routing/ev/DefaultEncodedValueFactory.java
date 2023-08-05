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
        } else if (SeasonalRestricted.KEY.equals(name)) {
            return SeasonalRestricted.create();
        } else if (GetOffBike.KEY.equals(name)) {
            return GetOffBike.create();
        } else if (RoadClass.KEY.equals(name)) {
            return RoadClass.create();
        } else if (RoadClassLink.KEY.equals(name)) {
            return RoadClassLink.create();
        } else if (RoadEnvironment.KEY.equals(name)) {
            return RoadEnvironment.create();
        } else if (RoadAccess.KEY.equals(name)) {
            return RoadAccess.create();
        } else if (MaxSpeed.KEY.equals(name)) {
            return MaxSpeed.create();
        } else if (MaxSpeedEstimated.KEY.equals(name)) {
            return MaxSpeedEstimated.create();
        } else if (MaxWeight.KEY.equals(name)) {
            return MaxWeight.create();
        } else if (MaxWeightExcept.KEY.equals(name)) {
            return MaxWeightExcept.create();
        } else if (MaxHeight.KEY.equals(name)) {
            return MaxHeight.create();
        } else if (MaxWidth.KEY.equals(name)) {
            return MaxWidth.create();
        } else if (MaxAxleLoad.KEY.equals(name)) {
            return MaxAxleLoad.create();
        } else if (MaxLength.KEY.equals(name)) {
            return MaxLength.create();
        } else if (Hgv.KEY.equals(name)) {
            return Hgv.create();
        } else if (Surface.KEY.equals(name)) {
            return Surface.create();
        } else if (Smoothness.KEY.equals(name)) {
            return Smoothness.create();
        } else if (Toll.KEY.equals(name)) {
            return Toll.create();
        } else if (TrackType.KEY.equals(name)) {
            return TrackType.create();
        } else if (BikeNetwork.KEY.equals(name) || FootNetwork.KEY.equals(name)) {
            return RouteNetwork.create(name);
        } else if (Hazmat.KEY.equals(name)) {
            return Hazmat.create();
        } else if (HazmatTunnel.KEY.equals(name)) {
            return HazmatTunnel.create();
        } else if (HazmatWater.KEY.equals(name)) {
            return HazmatWater.create();
        } else if (Lanes.KEY.equals(name)) {
            return Lanes.create();
        } else if (Footway.KEY.equals(name)) {
            return Footway.create();
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
        } else if (State.KEY.equals(name)) {
            return State.create();
        } else if (name.endsWith(Subnetwork.key(""))) {
            return Subnetwork.create(name);
        } else if (MaxSlope.KEY.equals(name)) {
            return MaxSlope.create();
        } else if (AverageSlope.KEY.equals(name)) {
            return AverageSlope.create();
        } else if (Curvature.KEY.equals(name)) {
            return Curvature.create();
        } else if (Crossing.KEY.equals(name)) {
            return new EnumEncodedValue<>(Crossing.KEY, Crossing.class);
        } else if (FerrySpeed.KEY.equals(name)) {
            return FerrySpeed.create();
        } else {
            throw new IllegalArgumentException("DefaultEncodedValueFactory cannot find EncodedValue " + name);
        }
    }
}
