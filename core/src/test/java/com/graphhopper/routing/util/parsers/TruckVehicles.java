package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.PMap;

import java.util.*;

/**
 * The vehicles of the former matrix TruckAccessParser, built from ModeAccessParser.Options
 */
class TruckVehicles {
    static final List<String> VEHICLES = List.of("car", "small_truck", "truck", "taxi", "scooter");

    static EncodingManager createEncodingManager() {
        EncodingManager.Builder builder = new EncodingManager.Builder().add(Roundabout.create());
        for (String vehicle : VEHICLES) builder.add(VehicleAccess.create(vehicle));
        return builder.build();
    }

    static ModeAccessParser create(EncodedValueLookup lookup, String vehicle, PMap properties) {
        Set<String> allow = new HashSet<>();
        if (!properties.getBool("block_private", true)) allow.addAll(List.of("private", "permit", "service"));
        if (!properties.getBool("block_delivery", true)) allow.add("delivery");
        if (!properties.getBool("block_military", true)) allow.add("military");
        List<String> keys = switch (vehicle) {
            case "car" -> List.of("motorcar", "motor_vehicle", "vehicle", "access");
            case "small_truck" -> List.of("goods", "motorcar", "motor_vehicle", "vehicle", "access");
            case "truck" -> List.of("hgv", "goods", "motor_vehicle", "vehicle", "access");
            case "taxi" -> List.of("taxi", "psv", "motorcar", "motor_vehicle", "vehicle", "access");
            case "scooter" -> List.of("goods", "moped", "motorcycle", "motor_vehicle", "vehicle", "access");
            default -> throw new IllegalArgumentException(vehicle);
        };
        ModeAccessParser.Options options = ModeAccessParser.Options.motorVehicle(keys).allow(allow, Set.of());
        VehicleConditions conditions = new VehicleConditions(keys, options.getAllowed());
        switch (vehicle) {
            case "car" -> conditions.height(1.99);
            case "small_truck" -> conditions.height(2.7).width(2, 0.28).length(5.5).weight(2.08 + 1.4);
            case "truck" -> {
                conditions.height(3.7).width(2.6, 0.34).length(12).weight(13.0 + 13.0).axes(3).hgv(true);
                options.trackTypes(Set.of());
            }
            case "taxi" -> options.backwardLaneKeys(List.of("psv", "taxi")).trackTypes(Set.of());
            case "scooter" -> {
                conditions.height(1.9).width(1, 0).length(2).weight(0.3);
                Set<String> allowed = new HashSet<>(options.getAllowed());
                allowed.add("dismount");
                Set<String> highways = new HashSet<>(ModeAccessParser.Options.CAR_HIGHWAYS);
                highways.add("cycleway");
                Set<String> barriers = new HashSet<>(ModeAccessParser.Options.CAR_BARRIERS);
                barriers.removeAll(List.of("bollard", "sump_buster"));
                options.values(allowed, options.getRestricted()).highways(highways).
                        barrierDefaults(ModeAccessParser.Options.barriers(barriers)).
                        blocked(ModeAccessParser.Options.explicitOnly("moped", allowed,
                                Set.of("motorway", "motorway_link", "cycleway", "pedestrian"), 50));
            }
        }
        return new ModeAccessParser(lookup.getBooleanEncodedValue(VehicleAccess.key(vehicle)),
                lookup.getBooleanEncodedValue(Roundabout.KEY), options.conditions(conditions));
    }

    static boolean forward(ModeAccessParser parser, ReaderWay way) {
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        parser.handleWayTags(0, access, way, null);
        return parser.getAccessEnc().getBool(false, 0, access);
    }

    static boolean backward(ModeAccessParser parser, ReaderWay way) {
        EdgeIntAccess access = new ArrayEdgeIntAccess(1);
        parser.handleWayTags(0, access, way, null);
        return parser.getAccessEnc().getBool(true, 0, access);
    }
}
