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
package com.graphhopper.routing.ev

import com.graphhopper.routing.util.FerrySpeedCalculator
import com.graphhopper.routing.util.PriorityCode
import com.graphhopper.routing.util.TransportationMode
import com.graphhopper.routing.util.parsers.*
import com.graphhopper.util.PMap
import java.util.function.BiFunction
import java.util.function.Function

open class DefaultImportRegistry : ImportRegistry {
    override fun createImportUnit(name: String): ImportUnit? {
        if (Roundabout.KEY == name)
            return ImportUnit.create(name, Function { props -> Roundabout.create() },
                    BiFunction { lookup, props ->
                        OSMRoundaboutParser(
                                lookup.getBooleanEncodedValue(Roundabout.KEY))
                    }
            )
        else if (GetOffBike.KEY == name)
            return ImportUnit.create(name, Function { props -> GetOffBike.create() },
                    BiFunction { lookup, pros ->
                        OSMGetOffBikeParser(
                                lookup.getBooleanEncodedValue(GetOffBike.KEY),
                                lookup.getBooleanEncodedValue("bike_access")
                        )
                    }, "bike_access")
        else if (RoadClass.KEY == name)
            return ImportUnit.create(name, Function { props -> RoadClass.create() },
                    BiFunction { lookup, props ->
                        OSMRoadClassParser(
                                lookup.getEnumEncodedValue(RoadClass.KEY, RoadClass::class.java))
                    }
            )
        else if (RoadClassLink.KEY == name)
            return ImportUnit.create(name, Function { props -> RoadClassLink.create() },
                    BiFunction { lookup, props ->
                        OSMRoadClassLinkParser(
                                lookup.getBooleanEncodedValue(RoadClassLink.KEY))
                    }
            )
        else if (RoadEnvironment.KEY == name)
            return ImportUnit.create(name, Function { props -> RoadEnvironment.create() },
                    BiFunction { lookup, props ->
                        OSMRoadEnvironmentParser(
                                lookup.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment::class.java))
                    }
            )
        else if (FootRoadAccess.KEY == name)
            return ImportUnit.create(name, Function { props -> FootRoadAccess.create() },
                    BiFunction { lookup, props ->
                        OSMRoadAccessParser.forFoot(
                                lookup.getEnumEncodedValue(FootRoadAccess.KEY, FootRoadAccess::class.java))
                    }
            )
        else if (BikeRoadAccess.KEY == name)
            return ImportUnit.create(name, Function { props -> BikeRoadAccess.create() },
                    BiFunction { lookup, props ->
                        OSMRoadAccessParser.forBike(
                                lookup.getEnumEncodedValue(BikeRoadAccess.KEY, BikeRoadAccess::class.java))
                    }
            )
        else if (RoadAccess.KEY == name)
            return ImportUnit.create(name, Function { props -> RoadAccess.create() },
                    BiFunction { lookup, props ->
                        OSMRoadAccessParser.forCar(
                                lookup.getEnumEncodedValue(RoadAccess.KEY, RoadAccess::class.java))
                    }
            )
        else if (MaxSpeed.KEY == name)
            return ImportUnit.create(name, Function { props -> MaxSpeed.create() },
                    BiFunction { lookup, props ->
                        OSMMaxSpeedParser(
                                lookup.getDecimalEncodedValue(MaxSpeed.KEY))
                    }
            )
        else if (MaxSpeedEstimated.KEY == name)
            return ImportUnit.create(name, Function { props -> MaxSpeedEstimated.create() },
                    null, Country.KEY, UrbanDensity.KEY)
        else if (UrbanDensity.KEY == name)
            return ImportUnit.create(name, Function { props -> UrbanDensity.create() },
                    null)
        else if (MaxWeight.KEY == name)
            return ImportUnit.create(name, Function { props -> MaxWeight.create() },
                    BiFunction { lookup, props ->
                        OSMMaxWeightParser(
                                lookup.getDecimalEncodedValue(MaxWeight.KEY))
                    }
            )
        else if (MaxWeightExcept.KEY == name)
            return ImportUnit.create(name, Function { props -> MaxWeightExcept.create() },
                    BiFunction { lookup, props ->
                        MaxWeightExceptParser(
                                lookup.getEnumEncodedValue(MaxWeightExcept.KEY, MaxWeightExcept::class.java))
                    }
            )
        else if (MaxHeight.KEY == name)
            return ImportUnit.create(name, Function { props -> MaxHeight.create() },
                    BiFunction { lookup, props ->
                        OSMMaxHeightParser(
                                lookup.getDecimalEncodedValue(MaxHeight.KEY))
                    }
            )
        else if (MaxWidth.KEY == name)
            return ImportUnit.create(name, Function { props -> MaxWidth.create() },
                    BiFunction { lookup, props ->
                        OSMMaxWidthParser(
                                lookup.getDecimalEncodedValue(MaxWidth.KEY))
                    }
            )
        else if (MaxAxleLoad.KEY == name)
            return ImportUnit.create(name, Function { props -> MaxAxleLoad.create() },
                    BiFunction { lookup, props ->
                        OSMMaxAxleLoadParser(
                                lookup.getDecimalEncodedValue(MaxAxleLoad.KEY))
                    }
            )
        else if (MaxLength.KEY == name)
            return ImportUnit.create(name, Function { props -> MaxLength.create() },
                    BiFunction { lookup, props ->
                        OSMMaxLengthParser(
                                lookup.getDecimalEncodedValue(MaxLength.KEY))
                    }
            )
        else if (Orientation.KEY == name)
            return ImportUnit.create(name, Function { props -> Orientation.create() },
                    BiFunction { lookup, props ->
                        OrientationCalculator(
                                lookup.getDecimalEncodedValue(Orientation.KEY))
                    }
            )
        else if (Surface.KEY == name)
            return ImportUnit.create(name, Function { props -> Surface.create() },
                    BiFunction { lookup, props ->
                        OSMSurfaceParser(
                                lookup.getEnumEncodedValue(Surface.KEY, Surface::class.java))
                    }
            )
        else if (Smoothness.KEY == name)
            return ImportUnit.create(name, Function { props -> Smoothness.create() },
                    BiFunction { lookup, props ->
                        OSMSmoothnessParser(
                                lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness::class.java))
                    }
            )
        else if (Hgv.KEY == name)
            return ImportUnit.create(name, Function { props -> Hgv.create() },
                    BiFunction { lookup, props ->
                        OSMHgvParser(
                                lookup.getEnumEncodedValue(Hgv.KEY, Hgv::class.java)
                        )
                    })
        else if (Toll.KEY == name)
            return ImportUnit.create(name, Function { props -> Toll.create() },
                    BiFunction { lookup, props ->
                        OSMTollParser(
                                lookup.getEnumEncodedValue(Toll.KEY, Toll::class.java))
                    }
            )
        else if (TrackType.KEY == name)
            return ImportUnit.create(name, Function { props -> TrackType.create() },
                    BiFunction { lookup, props ->
                        OSMTrackTypeParser(
                                lookup.getEnumEncodedValue(TrackType.KEY, TrackType::class.java))
                    }
            )
        else if (Hazmat.KEY == name)
            return ImportUnit.create(name, Function { props -> Hazmat.create() },
                    BiFunction { lookup, props ->
                        OSMHazmatParser(
                                lookup.getEnumEncodedValue(Hazmat.KEY, Hazmat::class.java))
                    }
            )
        else if (HazmatTunnel.KEY == name)
            return ImportUnit.create(name, Function { props -> HazmatTunnel.create() },
                    BiFunction { lookup, props ->
                        OSMHazmatTunnelParser(
                                lookup.getEnumEncodedValue(HazmatTunnel.KEY, HazmatTunnel::class.java))
                    }
            )
        else if (HazmatWater.KEY == name)
            return ImportUnit.create(name, Function { props -> HazmatWater.create() },
                    BiFunction { lookup, props ->
                        OSMHazmatWaterParser(
                                lookup.getEnumEncodedValue(HazmatWater.KEY, HazmatWater::class.java))
                    }
            )
        else if (Lanes.KEY == name)
            return ImportUnit.create(name, Function { props -> Lanes.create() },
                    BiFunction { lookup, props ->
                        OSMLanesParser(
                                lookup.getIntEncodedValue(Lanes.KEY))
                    }
            )
        else if (Footway.KEY == name)
            return ImportUnit.create(name, Function { props -> Footway.create() },
                    BiFunction { lookup, props ->
                        OSMFootwayParser(
                                lookup.getEnumEncodedValue(Footway.KEY, Footway::class.java))
                    }
            )
        else if (Sidewalk.KEY == name)
            return ImportUnit.create(name, Function { props -> Sidewalk.create() },
                    BiFunction { lookup, props ->
                        OSMSidewalkParser(
                                lookup.getEnumEncodedValue(Sidewalk.KEY, Sidewalk::class.java))
                    }
            )
        else if (Cycleway.KEY == name)
            return ImportUnit.create(name, Function { props -> Cycleway.create() },
                    BiFunction { lookup, props ->
                        OSMCyclewayParser(
                                lookup.getEnumEncodedValue(Cycleway.KEY, Cycleway::class.java))
                    }
            )
        else if (OSMWayID.KEY == name)
            return ImportUnit.create(name, Function { props -> OSMWayID.create() },
                    BiFunction { lookup, props ->
                        OSMWayIDParser(
                                lookup.getIntEncodedValue(OSMWayID.KEY))
                    }
            )
        else if (MtbRating.KEY == name)
            return ImportUnit.create(name, Function { props -> MtbRating.create() },
                    BiFunction { lookup, props ->
                        OSMMtbRatingParser(
                                lookup.getIntEncodedValue(MtbRating.KEY))
                    }
            )
        else if (HikeRating.KEY == name)
            return ImportUnit.create(name, Function { props -> HikeRating.create() },
                    BiFunction { lookup, props ->
                        OSMHikeRatingParser(
                                lookup.getIntEncodedValue(HikeRating.KEY))
                    }
            )
        else if (HorseRating.KEY == name)
            return ImportUnit.create(name, Function { props -> HorseRating.create() },
                    BiFunction { lookup, props ->
                        OSMHorseRatingParser(
                                lookup.getIntEncodedValue(HorseRating.KEY))
                    }
            )
        else if (Country.KEY == name)
            return ImportUnit.create(name, Function { props -> Country.create() },
                    BiFunction { lookup, props ->
                        CountryParser(
                                lookup.getEnumEncodedValue(Country.KEY, Country::class.java))
                    }
            )
        else if (State.KEY == name)
            return ImportUnit.create(name, Function { props -> State.create() },
                    BiFunction { lookup, props ->
                        StateParser(
                                lookup.getEnumEncodedValue(State.KEY, State::class.java))
                    }
            )
        else if (Crossing.KEY == name)
            return ImportUnit.create(name, Function { props -> Crossing.create() },
                    BiFunction { lookup, props ->
                        OSMCrossingParser(
                                lookup.getEnumEncodedValue(Crossing.KEY, Crossing::class.java))
                    }
            )
        else if (FerrySpeed.KEY == name)
            return ImportUnit.create(name, Function { props -> FerrySpeed.create() },
                    BiFunction { lookup, props ->
                        FerrySpeedCalculator(
                                lookup.getDecimalEncodedValue(FerrySpeed.KEY))
                    })
        else if (Curvature.KEY == name)
            return ImportUnit.create(name, Function { props -> Curvature.create() }, null)
        else if (AverageSlope.KEY == name)
            return ImportUnit.create(name, Function { props -> AverageSlope.create() }, null)
        else if (MaxSlope.KEY == name)
            return ImportUnit.create(name, Function { props -> MaxSlope.create() }, null)
        else if (BikeNetwork.KEY == name || MtbNetwork.KEY == name || FootNetwork.KEY == name)
            return ImportUnit.create(name, Function { props -> RouteNetwork.create(name) }, null)

        else if (BusAccess.KEY == name)
            return ImportUnit.create(name, Function { props -> BusAccess.create() },
                    BiFunction { lookup, props ->
                        ModeAccessParser(OSMRoadAccessParser.toOSMRestrictions(TransportationMode.BUS),
                                lookup.getBooleanEncodedValue(name), true, lookup.getBooleanEncodedValue(Roundabout.KEY),
                                PMap.toSet(props.getString("allow", "")), PMap.toSet(props.getString("restrict", "")))
                    },
                    "roundabout"
            )

        else if (HovAccess.KEY == name)
            return ImportUnit.create(name, Function { props -> HovAccess.create() },
                    BiFunction { lookup, props ->
                        ModeAccessParser(OSMRoadAccessParser.toOSMRestrictions(TransportationMode.HOV),
                                lookup.getBooleanEncodedValue(name), true, lookup.getBooleanEncodedValue(Roundabout.KEY),
                                PMap.toSet(props.getString("allow", "")), PMap.toSet(props.getString("restrict", "")))
                    },
                    "roundabout"
            )
        else if (FootTemporalAccess.KEY == name)
            return ImportUnit.create(name, Function { props -> FootTemporalAccess.create() },
                    BiFunction { lookup, props ->
                        val enc = lookup.getEnumEncodedValue(FootTemporalAccess.KEY, FootTemporalAccess::class.java)
                        val fct = OSMTemporalAccessParser.Setter { edgeId, edgeIntAccess, b ->
                            enc.setEnum(false, edgeId, edgeIntAccess, if (b) FootTemporalAccess.YES else FootTemporalAccess.NO)
                        }
                        OSMTemporalAccessParser(FootTemporalAccess.CONDITIONALS, fct, props.getString("date_range_parser_day", ""))
                    }
            )

        else if (BikeTemporalAccess.KEY == name)
            return ImportUnit.create(name, Function { props -> BikeTemporalAccess.create() },
                    BiFunction { lookup, props ->
                        val enc = lookup.getEnumEncodedValue(BikeTemporalAccess.KEY, BikeTemporalAccess::class.java)
                        val fct = OSMTemporalAccessParser.Setter { edgeId, edgeIntAccess, b ->
                            enc.setEnum(false, edgeId, edgeIntAccess, if (b) BikeTemporalAccess.YES else BikeTemporalAccess.NO)
                        }
                        OSMTemporalAccessParser(BikeTemporalAccess.CONDITIONALS, fct, props.getString("date_range_parser_day", ""))
                    }
            )

        else if (CarTemporalAccess.KEY == name)
            return ImportUnit.create(name, Function { props -> CarTemporalAccess.create() },
                    BiFunction { lookup, props ->
                        val enc = lookup.getEnumEncodedValue(CarTemporalAccess.KEY, CarTemporalAccess::class.java)
                        val fct = OSMTemporalAccessParser.Setter { edgeId, edgeIntAccess, b ->
                            enc.setEnum(false, edgeId, edgeIntAccess, if (b) CarTemporalAccess.YES else CarTemporalAccess.NO)
                        }
                        OSMTemporalAccessParser(CarTemporalAccess.CONDITIONALS, fct, props.getString("date_range_parser_day", ""))
                    }
            )

        else if (VehicleAccess.key("car") == name)
            return ImportUnit.create(name, Function { props -> VehicleAccess.create("car") },
                    BiFunction { lookup, props -> CarAccessParser(lookup, props) },
                    "roundabout"
            )
        else if (VehicleAccess.key("roads") == name)
            throw IllegalArgumentException("roads_access parser no longer necessary, see docs/migration/config-migration-08-09.md")
        else if (VehicleAccess.key("bike") == name)
            return ImportUnit.create(name, Function { props -> VehicleAccess.create("bike") },
                    BiFunction { lookup, props -> BikeAccessParser(lookup, props) },
                    "roundabout"
            )
        else if (VehicleAccess.key("racingbike") == name)
            return ImportUnit.create(name, Function { props -> VehicleAccess.create("racingbike") },
                    BiFunction { lookup, props -> RacingBikeAccessParser(lookup, props) },
                    "roundabout"
            )
        else if (VehicleAccess.key("mtb") == name)
            return ImportUnit.create(name, Function { props -> VehicleAccess.create("mtb") },
                    BiFunction { lookup, props -> MountainBikeAccessParser(lookup, props) },
                    "roundabout"
            )
        else if (VehicleAccess.key("foot") == name)
            return ImportUnit.create(name, Function { props -> VehicleAccess.create("foot") },
                    BiFunction { lookup, props -> FootAccessParser(lookup, props) })

        else if (VehicleSpeed.key("car") == name)
            return ImportUnit.create(name, Function { props ->
                DecimalEncodedValueImpl(
                        name, props.getInt("speed_bits", 7), props.getDouble("speed_factor", 2.0), true)
            },
                    BiFunction { lookup, props -> CarAverageSpeedParser(lookup) }
            )
        else if (VehicleSpeed.key("roads") == name)
            throw IllegalArgumentException("roads_average_speed parser no longer necessary, see docs/migration/config-migration-08-09.md")
        else if (VehicleSpeed.key("bike") == name)
            return ImportUnit.create(name, Function { props ->
                DecimalEncodedValueImpl(
                        name, props.getInt("speed_bits", 4), props.getDouble("speed_factor", 2.0), false)
            },
                    BiFunction { lookup, props -> BikeAverageSpeedParser(lookup) },
                    Smoothness.KEY
            )
        else if (VehicleSpeed.key("racingbike") == name)
            return ImportUnit.create(name, Function { props ->
                DecimalEncodedValueImpl(
                        name, props.getInt("speed_bits", 4), props.getDouble("speed_factor", 2.0), false)
            },
                    BiFunction { lookup, props -> RacingBikeAverageSpeedParser(lookup) },
                    Smoothness.KEY
            )
        else if (VehicleSpeed.key("mtb") == name)
            return ImportUnit.create(name, Function { props ->
                DecimalEncodedValueImpl(
                        name, props.getInt("speed_bits", 4), props.getDouble("speed_factor", 2.0), false)
            },
                    BiFunction { lookup, props -> MountainBikeAverageSpeedParser(lookup) },
                    Smoothness.KEY
            )
        else if (VehicleSpeed.key("foot") == name)
            return ImportUnit.create(name, Function { props ->
                DecimalEncodedValueImpl(
                        name, props.getInt("speed_bits", 4), props.getDouble("speed_factor", 1.0), false)
            },
                    BiFunction { lookup, props -> FootAverageSpeedParser(lookup) }
            )
        else if (VehiclePriority.key("foot") == name)
            return ImportUnit.create(name, Function { props -> VehiclePriority.create("foot", 4, PriorityCode.getFactor(1), false) },
                    BiFunction { lookup, props -> FootPriorityParser(lookup) },
                    RouteNetwork.key("foot")
            )
        else if (VehiclePriority.key("bike") == name)
            return ImportUnit.create(name, Function { props -> VehiclePriority.create("bike", 4, PriorityCode.getFactor(1), false) },
                    BiFunction { lookup, props -> BikePriorityParser(lookup) },
                    VehicleSpeed.key("bike"), BikeNetwork.KEY
            )
        else if (VehiclePriority.key("racingbike") == name)
            return ImportUnit.create(name, Function { props -> VehiclePriority.create("racingbike", 4, PriorityCode.getFactor(1), false) },
                    BiFunction { lookup, props -> RacingBikePriorityParser(lookup) },
                    VehicleSpeed.key("racingbike"), BikeNetwork.KEY
            )
        else if (VehiclePriority.key("mtb") == name)
            return ImportUnit.create(name, Function { props -> VehiclePriority.create("mtb", 4, PriorityCode.getFactor(1), false) },
                    BiFunction { lookup, props -> MountainBikePriorityParser(lookup) },
                    VehicleSpeed.key("mtb"), BikeNetwork.KEY, MtbNetwork.KEY
            )
        else if (Lit.KEY == name)
            return ImportUnit.create(name, Function { props -> Lit.create() },
                    BiFunction { lookup, props ->
                        OSMLitParser(
                                lookup.getBooleanEncodedValue(Lit.KEY))
                    }
            )
        return null
    }
}
