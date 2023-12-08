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
package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.util.PMap;

public class DefaultTagParserFactory implements TagParserFactory {

    @Override
    public TagParser create(EncodedValueLookup lookup, String name, PMap properties) {
        if (Roundabout.KEY.equals(name))
            return new OSMRoundaboutParser(lookup.getBooleanEncodedValue(Roundabout.KEY));
        else if (name.equals(RoadClass.KEY))
            return new OSMRoadClassParser(lookup.getEnumEncodedValue(RoadClass.KEY, RoadClass.class));
        else if (name.equals(RoadClassLink.KEY))
            return new OSMRoadClassLinkParser(lookup.getBooleanEncodedValue(RoadClassLink.KEY));
        else if (name.equals(RoadEnvironment.KEY))
            return new OSMRoadEnvironmentParser(lookup.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class));
        else if (name.equals(RoadAccess.KEY))
            return new OSMRoadAccessParser(lookup.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class), OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR));
        else if (name.equals(MaxSpeed.KEY))
            return new OSMMaxSpeedParser(lookup.getDecimalEncodedValue(MaxSpeed.KEY));
        else if (name.equals(MaxWeight.KEY))
            return new OSMMaxWeightParser(lookup.getDecimalEncodedValue(MaxWeight.KEY));
        else if (name.equals(MaxWeightExcept.KEY))
            return new MaxWeightExceptParser(lookup.getEnumEncodedValue(MaxWeightExcept.KEY, MaxWeightExcept.class));
        else if (name.equals(MaxHeight.KEY))
            return new OSMMaxHeightParser(lookup.getDecimalEncodedValue(MaxHeight.KEY));
        else if (name.equals(MaxWidth.KEY))
            return new OSMMaxWidthParser(lookup.getDecimalEncodedValue(MaxWidth.KEY));
        else if (name.equals(MaxAxleLoad.KEY))
            return new OSMMaxAxleLoadParser(lookup.getDecimalEncodedValue(MaxAxleLoad.KEY));
        else if (name.equals(MaxLength.KEY))
            return new OSMMaxLengthParser(lookup.getDecimalEncodedValue(MaxLength.KEY));
        else if (name.equals(Surface.KEY))
            return new OSMSurfaceParser(lookup.getEnumEncodedValue(Surface.KEY, Surface.class));
        else if (name.equals(Smoothness.KEY))
            return new OSMSmoothnessParser(lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class));
        else if (name.equals(Toll.KEY))
            return new OSMTollParser(lookup.getEnumEncodedValue(Toll.KEY, Toll.class));
        else if (name.equals(TrackType.KEY))
            return new OSMTrackTypeParser(lookup.getEnumEncodedValue(TrackType.KEY, TrackType.class));
        else if (name.equals(Hgv.KEY))
            return new OSMHgvParser(lookup.getEnumEncodedValue(Hgv.KEY, Hgv.class));
        else if (name.equals(Hazmat.KEY))
            return new OSMHazmatParser(lookup.getEnumEncodedValue(Hazmat.KEY, Hazmat.class));
        else if (name.equals(HazmatTunnel.KEY))
            return new OSMHazmatTunnelParser(lookup.getEnumEncodedValue(HazmatTunnel.KEY, HazmatTunnel.class));
        else if (name.equals(HazmatWater.KEY))
            return new OSMHazmatWaterParser(lookup.getEnumEncodedValue(HazmatWater.KEY, HazmatWater.class));
        else if (name.equals(Lanes.KEY))
            return new OSMLanesParser(lookup.getIntEncodedValue(Lanes.KEY));
        else if (name.equals(OSMWayID.KEY))
            return new OSMWayIDParser(lookup.getIntEncodedValue(OSMWayID.KEY));
        else if (name.equals(MtbRating.KEY))
            return new OSMMtbRatingParser(lookup.getIntEncodedValue(MtbRating.KEY));
        else if (name.equals(HikeRating.KEY))
            return new OSMHikeRatingParser(lookup.getIntEncodedValue(HikeRating.KEY));
        else if (name.equals(HorseRating.KEY))
            return new OSMHorseRatingParser(lookup.getIntEncodedValue(HorseRating.KEY));
        else if (name.equals(Footway.KEY))
            return new OSMFootwayParser(lookup.getEnumEncodedValue(Footway.KEY, Footway.class));
        else if (name.equals(Country.KEY))
            return new CountryParser(lookup.getEnumEncodedValue(Country.KEY, Country.class));
        else if (name.equals(State.KEY))
            return new StateParser(lookup.getEnumEncodedValue(State.KEY, State.class));
        else if (name.equals(Crossing.KEY))
            return new OSMCrossingParser(lookup.getEnumEncodedValue(Crossing.KEY, Crossing.class));
        else if (name.equals(FerrySpeed.KEY))
            return new FerrySpeedCalculator(lookup.getDecimalEncodedValue(FerrySpeed.KEY));
        else if (name.equals(BusAccess.KEY))
            return new ModeAccessParser(TransportationMode.BUS, lookup.getBooleanEncodedValue(BusAccess.KEY), lookup.getBooleanEncodedValue(Roundabout.KEY));
        return null;
    }
}
