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

import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;

public class DefaultTagParserFactory implements TagParserFactory {

    @Override
    public TagParser create(EncodedValueLookup lookup, PMap properties) {
        String name = properties.getString("name", "");
        if (Helper.isEmpty(name))
            throw new IllegalArgumentException("To create TagParser a name is required in the PMap: " + properties);

        if (VehicleAccess.key("car").equals(name))
            return new CarAccessParser(lookup, properties).init(properties.getObject("data_range_parser", new DateRangeParser()));
        else if (VehicleSpeed.key("car").equals(name))
            return new CarAverageSpeedParser(lookup, properties);

        else if (VehicleAccess.key("motorcycle").equals(name))
            return new MotorcycleAccessParser(lookup, properties).init(properties.getObject("data_range_parser", new DateRangeParser()));
        else if (VehicleSpeed.key("motorcycle").equals(name))
            return new MotorcycleAverageSpeedParser(lookup, properties);
        else if (VehiclePriority.key("motorcycle").equals(name))
            return new MotorcyclePriorityParser(lookup, properties);

        else if (VehicleAccess.key("roads").equals(name))
            return new RoadsAccessParser(lookup, properties);
        else if (VehicleSpeed.key("roads").equals(name))
            return new RoadsAverageSpeedParser(lookup, properties);

        else if (VehicleAccess.key("bike").equals(name))
            return new BikeAccessParser(lookup, properties).init(properties.getObject("data_range_parser", new DateRangeParser()));
        else if (VehicleSpeed.key("bike").equals(name))
            return new BikeAverageSpeedParser(lookup, properties);
        else if (VehiclePriority.key("bike").equals(name))
            return new BikePriorityParser(lookup, properties.putObject("average_speed", VehicleSpeed.key("bike")));

        else if (VehicleAccess.key("racingbike").equals(name))
            return new RacingBikeAccessParser(lookup, properties).init(properties.getObject("data_range_parser", new DateRangeParser()));
        else if (VehicleSpeed.key("racingbike").equals(name))
            return new RacingBikeAverageSpeedParser(lookup, properties);
        else if (VehiclePriority.key("racingbike").equals(name))
            return new RacingBikePriorityParser(lookup, properties.putObject("average_speed", VehicleSpeed.key("racingbike")));

        else if (VehicleAccess.key("mtb").equals(name))
            return new MountainBikeAccessParser(lookup, properties).init(properties.getObject("data_range_parser", new DateRangeParser()));
        else if (VehicleSpeed.key("mtb").equals(name))
            return new MountainBikeAverageSpeedParser(lookup, properties);
        else if (VehiclePriority.key("mtb").equals(name))
            return new MountainBikePriorityParser(lookup, properties.putObject("average_speed", VehicleSpeed.key("mtb")));

        else if (VehicleAccess.key("foot").equals(name))
            return new FootAccessParser(lookup, properties).init(properties.getObject("data_range_parser", new DateRangeParser()));
        else if (VehicleSpeed.key("foot").equals(name))
            return new FootAverageSpeedParser(lookup, properties);
        else if (VehiclePriority.key("foot").equals(name))
            return new FootPriorityParser(lookup, properties);

        else if (VehicleAccess.key("hike").equals(name))
            return new HikeAccessParser(lookup, properties).init(properties.getObject("data_range_parser", new DateRangeParser()));
        else if (VehicleSpeed.key("hike").equals(name))
            return new HikeAverageSpeedParser(lookup, properties);
        else if (VehiclePriority.key("hike").equals(name))
            return new HikePriorityParser(lookup, properties);

        else if (VehicleAccess.key("wheelchair").equals(name))
            return new WheelchairAccessParser(lookup, properties).init(properties.getObject("data_range_parser", new DateRangeParser()));
        else if (VehicleSpeed.key("wheelchair").equals(name))
            return new WheelchairAverageSpeedParser(lookup, properties);
        else if (VehiclePriority.key("wheelchair").equals(name))
            return new WheelchairPriorityParser(lookup, properties);

        else if (Roundabout.KEY.equals(name))
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
        return null;
    }
}
