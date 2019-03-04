package com.graphhopper.routing.util;

import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.parsers.*;
import com.graphhopper.util.PMap;

import static com.graphhopper.util.Helper.toLowerCase;

public class DefaultTagParserFactory implements TagParserFactory {
    @Override
    public TagParser create(String name, PMap configuration) {
        name = name.trim();
        if (!name.equals(toLowerCase(name)))
            throw new IllegalArgumentException("Use lower case for TagParsers: " + name);

        if (name.equals(RoadClass.KEY))
            return new OSMRoadClassParser();
        else if (name.equals(RoadClassLink.KEY))
            return new OSMRoadClassLinkParser();
        else if (name.equals(RoadEnvironment.KEY))
            return new OSMRoadEnvironmentParser();
        else if (name.equals(RoadAccess.KEY))
            return new OSMRoadAccessParser();
        else if (name.equals(Surface.KEY))
            return new OSMSurfaceParser();
        else if (name.equals(CarMaxSpeed.KEY))
            return new OSMCarMaxSpeedParser();
        else if (name.equals(Toll.KEY))
            return new OSMTollParser();
        throw new IllegalArgumentException("entry in encoder list not supported " + name);
    }
}
