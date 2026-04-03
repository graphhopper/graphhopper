package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.storage.IntsRef;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.graphhopper.routing.util.parsers.OSMTemporalAccessParser.hasPermissiveTemporalRestriction;

public class ModeAccessParser implements TagParser {

    private static final Set<String> INTENDED = Set.of("yes", "designated", "official", "permissive", "private", "permit", "destination");
    private static final Set<String> RESTRICTED = Set.of("no", "restricted", "military", "emergency",
            "delivery", "customers", "agricultural", "forestry", "service");
    static final Map<String, Map<String, String>> HIGHWAY_TYPE_DEFAULTS;
    static final Map<String, Map<String, String>> BARRIER_TYPE_DEFAULTS;

    static {
        // https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Access_restrictions
        Map<String, Map<String, String>> m = new HashMap<>();
        m.put("steps", Map.of("motor_vehicle", "no", "bicycle", "no", "foot", "designated"));
        m.put("footway", Map.of("motor_vehicle", "no", "bicycle", "no", "foot", "designated"));
        m.put("cycleway", Map.of("motor_vehicle", "no", "bicycle", "designated", "foot", "no"));
        m.put("pedestrian", Map.of("motor_vehicle", "no", "bicycle", "no", "foot", "designated"));
        m.put("path", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        m.put("bridleway", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "no"));
        m.put("busway", Map.of("access", "no", "bus", "designated"));
        HIGHWAY_TYPE_DEFAULTS = Map.copyOf(m);

        // https://wiki.openstreetmap.org/wiki/Key:barrier
        Map<String, Map<String, String>> b = new HashMap<>();
        b.put("fence", Map.of("access", "no"));
        b.put("wall", Map.of("access", "no"));
        b.put("hedge", Map.of("access", "no"));
        b.put("retaining_wall", Map.of("access", "no"));
        b.put("city_wall", Map.of("access", "no"));
        b.put("ditch", Map.of("access", "no"));
        b.put("kerb", Map.of("vehicle", "yes", "foot", "yes"));
        b.put("cattle_grid", Map.of("motor_vehicle", "yes", "foot", "yes", "bicycle", "yes"));
        b.put("bollard", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        b.put("block", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        b.put("log", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        b.put("chain", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        b.put("jersey_barrier", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        b.put("cycle_barrier", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        b.put("motorcycle_barrier", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        b.put("bus_trap", Map.of("motor_vehicle", "no", "bus", "yes", "foot", "yes", "bicycle", "yes"));
        b.put("sump_buster", Map.of("motor_vehicle", "no", "bus", "yes", "foot", "yes", "bicycle", "yes"));
        b.put("kissing_gate", Map.of("vehicle", "no", "foot", "yes"));
        b.put("stile", Map.of("vehicle", "no", "foot", "yes"));
        b.put("turnstile", Map.of("vehicle", "no", "foot", "yes"));
        BARRIER_TYPE_DEFAULTS = Map.copyOf(b);
    }
    private static final Set<String> ONEWAYS_FW = Set.of("yes", "true", "1");
    private final List<String> restrictionKeys;
    private final List<String> vehicleForward;
    private final List<String> vehicleBackward;
    private final List<String> ignoreOnewayKeys;
    private final BooleanEncodedValue accessEnc;
    private final BooleanEncodedValue roundaboutEnc;
    private final boolean skipEmergency;

    public ModeAccessParser(List<String> restrictionKeys, BooleanEncodedValue accessEnc,
                            boolean skipEmergency, BooleanEncodedValue roundaboutEnc) {
        this.accessEnc = accessEnc;
        this.roundaboutEnc = roundaboutEnc;
        this.restrictionKeys = restrictionKeys;
        vehicleForward = restrictionKeys.stream().map(r -> r + ":forward").toList();
        vehicleBackward = restrictionKeys.stream().map(r -> r + ":backward").toList();
        ignoreOnewayKeys = restrictionKeys.stream().map(r -> "oneway:" + r).toList();
        this.skipEmergency = skipEmergency;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String highwayValue = way.getTag("highway");
        if (skipEmergency && "service".equals(highwayValue) && "emergency_access".equals(way.getTag("service")))
            return;

        Map<String, String> defaults = highwayValue == null ? Map.of() : HIGHWAY_TYPE_DEFAULTS.getOrDefault(highwayValue, Map.of());
        int firstIndex = -1;
        String firstValue = "";
        for (int i = 0; i < restrictionKeys.size(); i++) {
            String key = restrictionKeys.get(i);
            String explicit = way.getTag(key);
            if (explicit != null) {
                if (INTENDED.contains(explicit) || RESTRICTED.contains(explicit)) {
                    firstIndex = i;
                    firstValue = explicit;
                    break;
                }
                // unknown value — fall through to implied default for same key, then keep looking
            }
            String implied = defaults.get(key);
            if (implied != null) {
                firstValue = implied;
                break;
            }
        }
        if (RESTRICTED.contains(firstValue) && !hasPermissiveTemporalRestriction(way, firstIndex, restrictionKeys, INTENDED))
            return;

        if (way.hasTag("gh:barrier_edge") && way.hasTag("node_tags")) {
            List<Map<String, Object>> nodeTags = way.getTag("node_tags", null);
            Map<String, Object> firstNodeTags = nodeTags.get(0);
            String barrierValue = firstNodeTags.containsKey("barrier") ? (String) firstNodeTags.get("barrier") : "";
            Map<String, String> barrierDefaults = BARRIER_TYPE_DEFAULTS.getOrDefault(barrierValue, Map.of());
            // Walk restriction keys checking explicit node tags and barrier type defaults
            String nodeValue = "";
            for (String key : restrictionKeys) {
                String explicit = (String) firstNodeTags.get(key);
                if (explicit != null) {
                    if (INTENDED.contains(explicit) || RESTRICTED.contains(explicit)) {
                        nodeValue = explicit;
                        break;
                    }
                }
                String implied = barrierDefaults.get(key);
                if (implied != null) {
                    nodeValue = implied;
                    break;
                }
            }
            if (RESTRICTED.contains(nodeValue))
                return;
            if ("yes".equals(firstNodeTags.get("locked")) && !INTENDED.contains(nodeValue))
                return;
        }

        if (FerrySpeedCalculator.isFerry(way)) {
            boolean isCar = restrictionKeys.contains("motorcar");
            if (INTENDED.contains(firstValue)
                    // implied default is allowed only if foot and bicycle is not specified:
                    || isCar && firstValue.isEmpty() && !way.hasTag("foot") && !way.hasTag("bicycle")
                    // if hgv is allowed then smaller trucks and cars are allowed too even if not specified
                    || isCar && way.hasTag("hgv", "yes")) {
                // ferry is allowed via explicit tag
            } else {
                return;
            }
        }

        boolean isRoundabout = roundaboutEnc.getBool(false, edgeId, edgeIntAccess);
        boolean ignoreOneway = "no".equals(way.getFirstValue(ignoreOnewayKeys));
        boolean isBwd = isBackwardOneway(way);
        if (!ignoreOneway && (isBwd || isRoundabout || isForwardOneway(way))) {
            accessEnc.setBool(isBwd, edgeId, edgeIntAccess, true);
        } else {
            accessEnc.setBool(false, edgeId, edgeIntAccess, true);
            accessEnc.setBool(true, edgeId, edgeIntAccess, true);
        }

    }

    protected boolean isBackwardOneway(ReaderWay way) {
        // vehicle:forward=no is like oneway=-1
        return way.hasTag("oneway", "-1") || "no".equals(way.getFirstValue(vehicleForward));
    }

    protected boolean isForwardOneway(ReaderWay way) {
        // vehicle:backward=no is like oneway=yes
        return way.hasTag("oneway", ONEWAYS_FW) || "no".equals(way.getFirstValue(vehicleBackward));
    }
}
