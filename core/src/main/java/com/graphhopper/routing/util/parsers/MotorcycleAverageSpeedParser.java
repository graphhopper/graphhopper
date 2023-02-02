package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.IntAccess;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.util.PMap;

public class MotorcycleAverageSpeedParser extends CarAverageSpeedParser {
    public static final double MOTORCYCLE_MAX_SPEED = 120;

    public MotorcycleAverageSpeedParser(EncodedValueLookup lookup, PMap properties) {
        this(
                lookup.getDecimalEncodedValue(VehicleSpeed.key(properties.getString("name", "motorcycle"))),
                lookup.getDecimalEncodedValue(VehicleSpeed.key(properties.getString("name", "motorcycle"))).getNextStorableValue(MOTORCYCLE_MAX_SPEED)
        );
    }

    public MotorcycleAverageSpeedParser(DecimalEncodedValue speedEnc, double maxPossibleSpeed) {
        super(speedEnc, maxPossibleSpeed);

        defaultSpeedMap.clear();

        // autobahn
        defaultSpeedMap.put("motorway", 100);
        defaultSpeedMap.put("motorway_link", 70);
        // bundesstraße
        defaultSpeedMap.put("trunk", 80);
        defaultSpeedMap.put("trunk_link", 75);
        // linking bigger town
        defaultSpeedMap.put("primary", 65);
        defaultSpeedMap.put("primary_link", 60);
        // linking towns + villages
        defaultSpeedMap.put("secondary", 60);
        defaultSpeedMap.put("secondary_link", 50);
        // streets without middle line separation
        defaultSpeedMap.put("tertiary", 50);
        defaultSpeedMap.put("tertiary_link", 40);
        defaultSpeedMap.put("unclassified", 30);
        defaultSpeedMap.put("residential", 30);
        // spielstraße
        defaultSpeedMap.put("living_street", 5);
        defaultSpeedMap.put("service", 20);
        // unknown road
        defaultSpeedMap.put("road", 20);
        // forestry stuff
        defaultSpeedMap.put("track", 15);

        trackTypeSpeedMap.clear();
        trackTypeSpeedMap.put("grade1", 20);
        trackTypeSpeedMap.put(null, defaultSpeedMap.get("track"));
    }

    @Override
    public void handleWayTags(int edgeId, IntAccess intAccess, ReaderWay way) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null) {
            if (way.hasTag("route", ferries)) {
                double ferrySpeed = ferrySpeedCalc.getSpeed(way);
                setSpeed(false, edgeId, intAccess, ferrySpeed);
                setSpeed(true, edgeId, intAccess, ferrySpeed);
            }
        } else {
            double speed = getSpeed(way);
            setSpeed(true, edgeId, intAccess, applyMaxSpeed(way, speed, true));
            setSpeed(false, edgeId, intAccess, applyMaxSpeed(way, speed, true));
        }
    }

    protected double applyMaxSpeed(ReaderWay way, double speed, boolean bwd) {
        speed = super.applyMaxSpeed(way, speed, bwd);
        double maxMCSpeed = OSMValueExtractor.stringToKmh(way.getTag("maxspeed:motorcycle"));
        if (isValidSpeed(maxMCSpeed))
            speed = Math.min(maxMCSpeed * 0.9, speed);

        // limit speed to max 30 km/h if bad surface
        if (isValidSpeed(speed) && speed > 30 && way.hasTag("surface", badSurfaceSpeedMap))
            speed = 30;
        return speed;
    }
}
