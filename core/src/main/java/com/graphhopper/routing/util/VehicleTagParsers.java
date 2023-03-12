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

package com.graphhopper.routing.util;

import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.util.parsers.*;
import com.graphhopper.util.PMap;

import java.util.Arrays;
import java.util.List;

public class VehicleTagParsers {
    private final TagParser accessParser;
    private final TagParser speedParser;
    private final TagParser priorityParser;

    public static VehicleTagParsers roads(EncodedValueLookup lookup, PMap properties) {
        return new VehicleTagParsers(
                new RoadsAccessParser(lookup, properties),
                new RoadsAverageSpeedParser(lookup, properties),
                null
        );
    }

    public static VehicleTagParsers car(EncodedValueLookup lookup, PMap properties) {
        return new VehicleTagParsers(
                new CarAccessParser(lookup, properties).init(properties.getObject("date_range_parser", new DateRangeParser())),
                new CarAverageSpeedParser(lookup, properties),
                null
        );
    }

    public static VehicleTagParsers bike(EncodedValueLookup lookup, PMap properties) {
        return new VehicleTagParsers(
                new BikeAccessParser(lookup, properties).init(properties.getObject("date_range_parser", new DateRangeParser())),
                new BikeAverageSpeedParser(lookup, properties),
                new BikePriorityParser(lookup, properties)
        );
    }

    public static VehicleTagParsers racingbike(EncodedValueLookup lookup, PMap properties) {
        return new VehicleTagParsers(
                new RacingBikeAccessParser(lookup, properties).init(properties.getObject("date_range_parser", new DateRangeParser())),
                new RacingBikeAverageSpeedParser(lookup, properties),
                new RacingBikePriorityParser(lookup, properties)
        );
    }

    public static VehicleTagParsers mtb(EncodedValueLookup lookup, PMap properties) {
        return new VehicleTagParsers(
                new MountainBikeAccessParser(lookup, properties).init(properties.getObject("date_range_parser", new DateRangeParser())),
                new MountainBikeAverageSpeedParser(lookup, properties),
                new MountainBikePriorityParser(lookup, properties)
        );
    }

    public static VehicleTagParsers foot(EncodedValueLookup lookup, PMap properties) {
        return new VehicleTagParsers(
                new FootAccessParser(lookup, properties).init(properties.getObject("date_range_parser", new DateRangeParser())),
                new FootAverageSpeedParser(lookup, properties),
                new FootPriorityParser(lookup, properties)
        );
    }

    public static VehicleTagParsers motorcycle(EncodedValueLookup lookup, PMap properties) {
        return new VehicleTagParsers(
                new MotorcycleAccessParser(lookup, properties).init(properties.getObject("date_range_parser", new DateRangeParser())),
                new MotorcycleAverageSpeedParser(lookup, properties),
                new MotorcyclePriorityParser(lookup, properties)
        );
    }

    public static VehicleTagParsers wheelchair(EncodedValueLookup lookup, PMap properties) {
        return new VehicleTagParsers(
                new WheelchairAccessParser(lookup, properties).init(properties.getObject("date_range_parser", new DateRangeParser())),
                new WheelchairAverageSpeedParser(lookup, properties),
                new WheelchairPriorityParser(lookup, properties)
        );
    }

    public VehicleTagParsers(TagParser accessParser, TagParser speedParser, TagParser priorityParser) {
        this.accessParser = accessParser;
        this.speedParser = speedParser;
        this.priorityParser = priorityParser;
    }

    public TagParser getAccessParser() {
        return accessParser;
    }

    public TagParser getSpeedParser() {
        return speedParser;
    }

    public TagParser getPriorityParser() {
        return priorityParser;
    }

    public List<TagParser> getTagParsers() {
        return Arrays.asList(accessParser, speedParser, priorityParser);
    }

}
