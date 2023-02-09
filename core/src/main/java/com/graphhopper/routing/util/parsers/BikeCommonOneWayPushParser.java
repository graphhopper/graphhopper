package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.storage.IntsRef;

import java.util.*;

import static com.graphhopper.routing.util.parsers.AbstractAccessParser.FERRIES;
import static com.graphhopper.routing.util.parsers.AbstractAccessParser.INTENDED;

public abstract class BikeCommonOneWayPushParser implements TagParser {

    protected static final int PUSHING_SECTION_SPEED = 4;
    protected static final int MIN_SPEED = 2;

    // Pushing section highways are parts where you need to get off your bike and push it (German: Schiebestrecke)
    protected final HashSet<String> pushingSectionsHighways = new HashSet<>();
    private final Map<String, Integer> highwaySpeeds = new HashMap<>();
    protected final Set<String> ferries = new HashSet<>(FERRIES);
    protected final Set<String> intendedValues = new HashSet<>(INTENDED);
    protected final Set<String> amenitiesValues = new HashSet<>(FERRIES);

    protected final DecimalEncodedValue avgSpeedEnc;
    protected final BooleanEncodedValue accessEnc;

    protected BikeCommonOneWayPushParser(BooleanEncodedValue accessEnc, DecimalEncodedValue avgSpeedEnc) {

        this.accessEnc = accessEnc;
        this.avgSpeedEnc = avgSpeedEnc;

        // duplicate code as also in BikeCommonAverageSpeedParser
        addPushingSection("footway");
        addPushingSection("pedestrian");
        addPushingSection("steps");
        addPushingSection("platform");

        setHighwaySpeed("cycleway", PUSHING_SECTION_SPEED);
        setHighwaySpeed("path", PUSHING_SECTION_SPEED);
        setHighwaySpeed("footway", PUSHING_SECTION_SPEED);
        setHighwaySpeed("platform", PUSHING_SECTION_SPEED);
        setHighwaySpeed("pedestrian", PUSHING_SECTION_SPEED);
        setHighwaySpeed("track", PUSHING_SECTION_SPEED);
        setHighwaySpeed("service", PUSHING_SECTION_SPEED);
        setHighwaySpeed("residential", PUSHING_SECTION_SPEED);
        // no other highway applies:
        setHighwaySpeed("unclassified", PUSHING_SECTION_SPEED);
        // unknown road:
        setHighwaySpeed("road", PUSHING_SECTION_SPEED);

        setHighwaySpeed("trunk", PUSHING_SECTION_SPEED);
        setHighwaySpeed("trunk_link", PUSHING_SECTION_SPEED);
        setHighwaySpeed("primary", PUSHING_SECTION_SPEED);
        setHighwaySpeed("primary_link", PUSHING_SECTION_SPEED);
        setHighwaySpeed("secondary", PUSHING_SECTION_SPEED);
        setHighwaySpeed("secondary_link", PUSHING_SECTION_SPEED);
        setHighwaySpeed("tertiary", PUSHING_SECTION_SPEED);
        setHighwaySpeed("tertiary_link", PUSHING_SECTION_SPEED);

        setHighwaySpeed("motorway", PUSHING_SECTION_SPEED);
        setHighwaySpeed("motorway_link", PUSHING_SECTION_SPEED);
        setHighwaySpeed("steps", MIN_SPEED);

        setHighwaySpeed("bridleway", PUSHING_SECTION_SPEED);

    }

    @Override
    public void handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {

        boolean backwardInaccessible = !accessEnc.getBool(true,edgeFlags);
        boolean forwardInaccessible = !accessEnc.getBool(false,edgeFlags);

        double backwardSpeed = avgSpeedEnc.getDecimal(true,edgeFlags);
        double forwardSpeed = avgSpeedEnc.getDecimal(false,edgeFlags);

        String highwayTag = way.getTag("highway");
        double pushHighwaySpeed = PUSHING_SECTION_SPEED;
        if(isSetHighwaySpeed(highwayTag)){
            double highwaySpeed = getHighwaySpeed(highwayTag);
            if(highwaySpeed < pushHighwaySpeed) {
                pushHighwaySpeed = highwaySpeed;
            }
        }


        // pushing bikes - if no other mode found
        if(forwardInaccessible || backwardInaccessible
                || isInvalidSpeed(forwardSpeed)
                || isInvalidSpeed(backwardSpeed)) {

            if (!way.hasTag("foot", "no")) {

                boolean implyOneWay = implyOneWay(way);
                boolean wayTypeAllowPushing = wayTypeAllowPushing(way,highwayTag);

                double pushForwardSpeed = Double.POSITIVE_INFINITY;
                double pushBackwardSpeed = Double.POSITIVE_INFINITY;

                if (way.hasTag("highway", pushingSectionsHighways)) {
                    pushForwardSpeed = pushHighwaySpeed;
                    pushBackwardSpeed = pushHighwaySpeed;
                } else {
                    if (way.hasTag("foot", "yes")) {
                        pushForwardSpeed = pushHighwaySpeed;
                        if (!implyOneWay) {
                            pushBackwardSpeed = pushHighwaySpeed;
                        }
                    } else if (way.hasTag("foot:forward", "yes")) {
                        pushForwardSpeed = pushHighwaySpeed;
                    } else if (way.hasTag("foot:backward", "yes")) {
                        pushBackwardSpeed = pushHighwaySpeed;
                    } else if (wayTypeAllowPushing) {
                        pushForwardSpeed = pushHighwaySpeed;
                        if (!implyOneWay) {
                            pushBackwardSpeed = pushHighwaySpeed;
                        }
                    }
                }

                if (isValidSpeed(pushForwardSpeed) && (forwardInaccessible || isInvalidSpeed(forwardSpeed))) {
                    accessEnc.setBool(false, edgeFlags, true);
                    avgSpeedEnc.setDecimal(false, edgeFlags, pushForwardSpeed);
                }

                if (isValidSpeed(pushBackwardSpeed) && (backwardInaccessible || isInvalidSpeed(backwardSpeed))) {
                    if(accessEnc.isStoreTwoDirections() && avgSpeedEnc.isStoreTwoDirections()) {
                        accessEnc.setBool(true, edgeFlags, true);
                        avgSpeedEnc.setDecimal(true, edgeFlags, pushBackwardSpeed);
                    }
                }

            }
        }

        // dismount
        if (way.hasTag("bicycle", "dismount")){
            accessEnc.setBool(false, edgeFlags, true);
            avgSpeedEnc.setDecimal(false, edgeFlags, PUSHING_SECTION_SPEED);

            if(accessEnc.isStoreTwoDirections() && avgSpeedEnc.isStoreTwoDirections()){
                accessEnc.setBool(true, edgeFlags, true);
                avgSpeedEnc.setDecimal(true, edgeFlags, PUSHING_SECTION_SPEED);
            }
        }


    }

    boolean isInvalidSpeed(double speed){
        return !isValidSpeed(speed);
    }
    boolean isValidSpeed(double speed){
        return speed != Double.POSITIVE_INFINITY;
    }

    boolean implyOneWay(ReaderWay way){
        return way.hasTag("junction", "roundabout")
                || way.hasTag("junction", "circular")
                || way.hasTag("highway", "motorway");
    }

    boolean wayTypeAllowPushing(ReaderWay way, String highwayTag){
        return way.hasTag("railway", "platform")
                || way.hasTag("bridge", "movable")
                || way.hasTag("public_transport", "platform")
                || way.hasTag("amenity", "parking", "parking_entrance")
                || isSetHighwaySpeed(highwayTag)
                || way.hasTag("access", intendedValues);

    }

    // TODO duplicated in average speed
    void addPushingSection(String highway) {
        pushingSectionsHighways.add(highway);
    }

    void setHighwaySpeed(String highway, int speed) {
        highwaySpeeds.put(highway, speed);
    }

    int getHighwaySpeed(String key) {
        return highwaySpeeds.get(key);
    }

    boolean isSetHighwaySpeed(String key){
        return highwaySpeeds.containsKey(key);
    }
}