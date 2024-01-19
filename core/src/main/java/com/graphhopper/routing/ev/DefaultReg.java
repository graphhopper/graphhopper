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

import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.util.parsers.*;

public class DefaultReg implements Reg {
    @Override
    public RegEntry getRegEntry(String name) {
        if (Roundabout.KEY.equals(name))
            return RegEntry.create(name, props -> Roundabout.create(),
                    (lookup, props) -> new OSMRoundaboutParser(
                            lookup.getBooleanEncodedValue(Roundabout.KEY))
            );
        else if (GetOffBike.KEY.equals(name))
            return RegEntry.create(name, props -> GetOffBike.create(),
                    (lookup, pros) -> new OSMGetOffBikeParser(
                            lookup.getBooleanEncodedValue(GetOffBike.KEY),
                            // todonow: in master we use the *first* bike?!
                            lookup.getBooleanEncodedValue("bike_access")
                    ), "bike_access");
        else if (RoadClass.KEY.equals(name))
            return RegEntry.create(name, props -> RoadClass.create(),
                    (lookup, props) -> new OSMRoadClassParser(
                            lookup.getEnumEncodedValue(RoadClass.KEY, RoadClass.class))
            );
        else if (RoadClassLink.KEY.equals(name))
            return RegEntry.create(name, props -> RoadClassLink.create(),
                    (lookup, props) -> new OSMRoadClassLinkParser(
                            lookup.getBooleanEncodedValue(RoadClassLink.KEY))
            );
        else if (RoadEnvironment.KEY.equals(name))
            return RegEntry.create(name, props -> RoadEnvironment.create(),
                    (lookup, props) -> new OSMRoadEnvironmentParser(
                            lookup.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class))
            );
        else if (RoadAccess.KEY.equals(name))
            return RegEntry.create(name, props -> RoadAccess.create(),
                    (lookup, props) -> new OSMRoadAccessParser(
                            lookup.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class),
                            OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR))
            );
        else if (MaxSpeed.KEY.equals(name))
            return RegEntry.create(name, props -> MaxSpeed.create(),
                    (lookup, props) -> new OSMMaxSpeedParser(
                            lookup.getDecimalEncodedValue(MaxSpeed.KEY))
            );
        else if (MaxSpeedEstimated.KEY.equals(name))
            return RegEntry.create(name, props -> MaxSpeedEstimated.create(),
                    null, Country.KEY, UrbanDensity.KEY);
        else if (UrbanDensity.KEY.equals(name))
            return RegEntry.create(name, props -> UrbanDensity.create(),
                    null);
        else if (MaxWeight.KEY.equals(name))
            return RegEntry.create(name, props -> MaxWeight.create(),
                    (lookup, props) -> new OSMMaxWeightParser(
                            lookup.getDecimalEncodedValue(MaxWeight.KEY))
            );
        else if (MaxWeightExcept.KEY.equals(name))
            return RegEntry.create(name, props -> MaxWeightExcept.create(),
                    (lookup, props) -> new MaxWeightExceptParser(
                            lookup.getEnumEncodedValue(MaxWeightExcept.KEY, MaxWeightExcept.class))
            );
        else if (MaxHeight.KEY.equals(name))
            return RegEntry.create(name, props -> MaxHeight.create(),
                    (lookup, props) -> new OSMMaxHeightParser(
                            lookup.getDecimalEncodedValue(MaxHeight.KEY))
            );
        else if (MaxWidth.KEY.equals(name))
            return RegEntry.create(name, props -> MaxWidth.create(),
                    (lookup, props) -> new OSMMaxWidthParser(
                            lookup.getDecimalEncodedValue(MaxWidth.KEY))
            );
        else if (MaxAxleLoad.KEY.equals(name))
            return RegEntry.create(name, props -> MaxAxleLoad.create(),
                    (lookup, props) -> new OSMMaxAxleLoadParser(
                            lookup.getDecimalEncodedValue(MaxAxleLoad.KEY))
            );
        else if (MaxLength.KEY.equals(name))
            return RegEntry.create(name, props -> MaxLength.create(),
                    (lookup, props) -> new OSMMaxLengthParser(
                            lookup.getDecimalEncodedValue(MaxLength.KEY))
            );
        else if (Surface.KEY.equals(name))
            return RegEntry.create(name, props -> Surface.create(),
                    (lookup, props) -> new OSMSurfaceParser(
                            lookup.getEnumEncodedValue(Surface.KEY, Surface.class))
            );
        else if (Smoothness.KEY.equals(name))
            return RegEntry.create(name, props -> Smoothness.create(),
                    (lookup, props) -> new OSMSmoothnessParser(
                            lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class))
            );
        else if (Hgv.KEY.equals(name))
            return RegEntry.create(name, props -> Hgv.create(),
                    (lookup, props) -> new OSMHgvParser(
                            lookup.getEnumEncodedValue(Hgv.KEY, Hgv.class)
                    ));
        else if (Toll.KEY.equals(name))
            return RegEntry.create(name, props -> Toll.create(),
                    (lookup, props) -> new OSMTollParser(
                            lookup.getEnumEncodedValue(Toll.KEY, Toll.class))
            );
        else if (TrackType.KEY.equals(name))
            return RegEntry.create(name, props -> TrackType.create(),
                    (lookup, props) -> new OSMTrackTypeParser(
                            lookup.getEnumEncodedValue(TrackType.KEY, TrackType.class))
            );
        else if (Hazmat.KEY.equals(name))
            return RegEntry.create(name, props -> Hazmat.create(),
                    (lookup, props) -> new OSMHazmatParser(
                            lookup.getEnumEncodedValue(Hazmat.KEY, Hazmat.class))
            );
        else if (HazmatTunnel.KEY.equals(name))
            return RegEntry.create(name, props -> HazmatTunnel.create(),
                    (lookup, props) -> new OSMHazmatTunnelParser(
                            lookup.getEnumEncodedValue(HazmatTunnel.KEY, HazmatTunnel.class))
            );
        else if (HazmatWater.KEY.equals(name))
            return RegEntry.create(name, props -> HazmatWater.create(),
                    (lookup, props) -> new OSMHazmatWaterParser(
                            lookup.getEnumEncodedValue(HazmatWater.KEY, HazmatWater.class))
            );
        else if (Lanes.KEY.equals(name))
            return RegEntry.create(name, props -> Lanes.create(),
                    (lookup, props) -> new OSMLanesParser(
                            lookup.getIntEncodedValue(Lanes.KEY))
            );
        else if (Footway.KEY.equals(name))
            return RegEntry.create(name, props -> Footway.create(),
                    (lookup, props) -> new OSMFootwayParser(
                            lookup.getEnumEncodedValue(Footway.KEY, Footway.class))
            );
        else if (OSMWayID.KEY.equals(name))
            return RegEntry.create(name, props -> OSMWayID.create(),
                    (lookup, props) -> new OSMWayIDParser(
                            lookup.getIntEncodedValue(OSMWayID.KEY))
            );
        else if (MtbRating.KEY.equals(name))
            return RegEntry.create(name, props -> MtbRating.create(),
                    (lookup, props) -> new OSMMtbRatingParser(
                            lookup.getIntEncodedValue(MtbRating.KEY))
            );
        else if (HikeRating.KEY.equals(name))
            return RegEntry.create(name, props -> HikeRating.create(),
                    (lookup, props) -> new OSMHikeRatingParser(
                            lookup.getIntEncodedValue(HikeRating.KEY))
            );
        else if (HorseRating.KEY.equals(name))
            return RegEntry.create(name, props -> HorseRating.create(),
                    (lookup, props) -> new OSMHorseRatingParser(
                            lookup.getIntEncodedValue(HorseRating.KEY))
            );
        else if (Country.KEY.equals(name))
            return RegEntry.create(name, props -> Country.create(),
                    (lookup, props) -> new CountryParser(
                            lookup.getEnumEncodedValue(Country.KEY, Country.class))
            );
        else if (State.KEY.equals(name))
            return RegEntry.create(name, props -> State.create(),
                    (lookup, props) -> new StateParser(
                            lookup.getEnumEncodedValue(State.KEY, State.class))
            );
        else if (Crossing.KEY.equals(name))
            return RegEntry.create(name, props -> Crossing.create(),
                    (lookup, props) -> new OSMCrossingParser(
                            lookup.getEnumEncodedValue(Crossing.KEY, Crossing.class))
            );
        else if (FerrySpeed.KEY.equals(name))
            return RegEntry.create(name, props -> FerrySpeed.create(),
                    (lookup, props) -> new FerrySpeedCalculator(
                            lookup.getDecimalEncodedValue(FerrySpeed.KEY)));
        else if (Curvature.KEY.equals(name))
            return RegEntry.create(name, props -> Curvature.create(),
                    (lookup, props) -> new CurvatureCalculator(
                            lookup.getDecimalEncodedValue(Curvature.KEY))
            );
        else if (AverageSlope.KEY.equals(name))
            return RegEntry.create(name, props -> AverageSlope.create(),
                    (lookup, props) -> new SlopeCalculator(
                            lookup.getDecimalEncodedValue(MaxSlope.KEY),
                            lookup.getDecimalEncodedValue(AverageSlope.KEY)
                    ));
        else if (MaxSlope.KEY.equals(name))
            return RegEntry.create(name, props -> MaxSlope.create(),
                    // todonow: we need to make sure that if one of average/max_slope is enabled the other is too
                    null, AverageSlope.KEY);
        else if (BikeNetwork.KEY.equals(name) || MtbNetwork.KEY.equals(name) || FootNetwork.KEY.equals(name))
            return RegEntry.create(name, props -> RouteNetwork.create(name), null);

        else if (VehicleAccess.key("car").equals(name))
            return RegEntry.create(name, props -> VehicleAccess.create("car"),
                    (lookup, props) -> new CarAccessParser(lookup, props).init(props.getObject("date_range_parser", new DateRangeParser())),
                    "roundabout"
            );
        else if (VehicleAccess.key("roads").equals(name))
            return RegEntry.create(name, props -> VehicleAccess.create("roads"),
                    (lookup, props) -> new RoadsAccessParser(lookup)
            );
        else if (VehicleAccess.key("bike").equals(name))
            return RegEntry.create(name, props -> VehicleAccess.create("bike"),
                    (lookup, props) -> new BikeAccessParser(lookup, props).init(props.getObject("date_range_parser", new DateRangeParser())),
                    "roundabout"
            );
        else if (VehicleAccess.key("racingbike").equals(name))
            return RegEntry.create(name, props -> VehicleAccess.create("racingbike"),
                    (lookup, props) -> new RacingBikeAccessParser(lookup, props).init(props.getObject("date_range_parser", new DateRangeParser())),
                    "roundabout"
            );
        else if (VehicleAccess.key("mtb").equals(name))
            return RegEntry.create(name, props -> VehicleAccess.create("mtb"),
                    (lookup, props) -> new MountainBikeAccessParser(lookup, props).init(props.getObject("date_range_parser", new DateRangeParser())),
                    "roundabout"
            );
        else if (VehicleAccess.key("foot").equals(name))
            return RegEntry.create(name, props -> VehicleAccess.create("foot"),
                    (lookup, props) -> new FootAccessParser(lookup, props).init(props.getObject("date_range_parser", new DateRangeParser())));

        else if (VehicleSpeed.key("car").equals(name))
            return RegEntry.create(name, props -> new DecimalEncodedValueImpl(
                            name, props.getInt("speed_bits", 7), props.getDouble("speed_factor", 2), true),
                    (lookup, props) -> new CarAverageSpeedParser(lookup),
                    "ferry_speed"
            );
        else if (VehicleSpeed.key("roads").equals(name))
            return RegEntry.create(name, props -> new DecimalEncodedValueImpl(
                            name, props.getInt("speed_bits", 7), props.getDouble("speed_factor", 2), true),
                    (lookup, props) -> new RoadsAverageSpeedParser(lookup)
            );
        else if (VehicleSpeed.key("bike").equals(name))
            return RegEntry.create(name, props -> new DecimalEncodedValueImpl(
                            name, props.getInt("speed_bits", 4), props.getDouble("speed_factor", 2), false),
                    (lookup, props) -> new BikeAverageSpeedParser(lookup),
                    "ferry_speed", "smoothness"
            );
        else if (VehicleSpeed.key("racingbike").equals(name))
            return RegEntry.create(name, props -> new DecimalEncodedValueImpl(
                            name, props.getInt("speed_bits", 4), props.getDouble("speed_factor", 2), false),
                    (lookup, props) -> new RacingBikeAverageSpeedParser(lookup),
                    "ferry_speed", "smoothness"
            );
        else if (VehicleSpeed.key("mtb").equals(name))
            return RegEntry.create(name, props -> new DecimalEncodedValueImpl(
                            name, props.getInt("speed_bits", 4), props.getDouble("speed_factor", 2), false),
                    (lookup, props) -> new MountainBikeAverageSpeedParser(lookup),
                    "ferry_speed", "smoothness"
            );
        else if (VehicleSpeed.key("foot").equals(name))
            return RegEntry.create(name, props -> new DecimalEncodedValueImpl(
                            name, props.getInt("speed_bits", 4), props.getDouble("speed_factor", 1), false),
                    (lookup, props) -> new FootAverageSpeedParser(lookup),
                    "ferry_speed"
            );
        else if (VehiclePriority.key("foot").equals(name))
            return RegEntry.create(name, props -> VehiclePriority.create("foot", 4, PriorityCode.getFactor(1), false),
                    (lookup, props) -> new FootPriorityParser(lookup),
                    RouteNetwork.key("foot")
            );
        else if (VehiclePriority.key("bike").equals(name))
            return RegEntry.create(name, props -> VehiclePriority.create("bike", 4, PriorityCode.getFactor(1), false),
                    (lookup, props) -> new BikePriorityParser(lookup),
                    VehicleSpeed.key("bike"), BikeNetwork.KEY
            );
        else if (VehiclePriority.key("racingbike").equals(name))
            return RegEntry.create(name, props -> VehiclePriority.create("racingbike", 4, PriorityCode.getFactor(1), false),
                    (lookup, props) -> new RacingBikePriorityParser(lookup),
                    VehicleSpeed.key("racingbike"), BikeNetwork.KEY
            );
        else if (VehiclePriority.key("mtb").equals(name))
            return RegEntry.create(name, props -> VehiclePriority.create("mtb", 4, PriorityCode.getFactor(1), false),
                    (lookup, props) -> new MountainBikePriorityParser(lookup),
                    VehicleSpeed.key("mtb"), BikeNetwork.KEY
            );
        return null;
    }
}
