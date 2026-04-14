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

    private static final Set<String> CAR_BARRIERS = Set.of("kissing_gate", "fence",
            "bollard", "stile", "turnstile", "cycle_barrier", "motorcycle_barrier", "block",
            "bus_trap", "sump_buster", "jersey_barrier");
    private static final Set<String> INTENDED = Set.of("yes", "designated", "official", "permissive", "private", "permit", "destination");
    static final Map<String, Map<String, String>> HIGHWAY_TYPE_DEFAULTS;
    static final Map<String, Map<String, String>> BARRIER_TYPE_DEFAULTS;

    // default implied access for unknown highway types
    private static final Map<String, String> UNKNOWN_HIGHWAY_DEFAULT = Map.of("access", "no");

    static {
        Map<String, Map<String, String>> m = new HashMap<>();
        // routable highway types with no special access defaults (empty map = fully accessible)
        for (String hw : List.of("motorway", "motorway_link", "trunk", "trunk_link",
                "primary", "primary_link", "secondary", "secondary_link",
                "tertiary", "tertiary_link", "unclassified", "residential",
                "living_street", "service", "road", "track"))
            m.put(hw, Map.of());
        // routable highway types with specific access defaults
        m.put("steps", Map.of("access", "no"));
        m.put("footway", Map.of("motor_vehicle", "no"));
        m.put("cycleway", Map.of("motor_vehicle", "no"));
        m.put("pedestrian", Map.of("motor_vehicle", "no"));
        m.put("path", Map.of("motor_vehicle", "no"));
        m.put("bridleway", Map.of("motor_vehicle", "no"));
        m.put("busway", Map.of("access", "no", "bus", "designated"));
        HIGHWAY_TYPE_DEFAULTS = Map.copyOf(m);

        Map<String, Map<String, String>> b = new HashMap<>();
        b.put("bus_trap", Map.of("motor_vehicle", "no", "bus", "yes"));
        b.put("sump_buster", Map.of("motor_vehicle", "no", "bus", "yes"));
        BARRIER_TYPE_DEFAULTS = Map.copyOf(b);
    }
    private static final Set<String> ONEWAYS_FW = Set.of("yes", "true", "1");
    private final Set<String> restrictedValues;
    private final List<String> restrictionKeys;
    private final List<String> vehicleForward;
    private final List<String> vehicleBackward;
    private final List<String> ignoreOnewayKeys;
    private final BooleanEncodedValue accessEnc;
    private final BooleanEncodedValue roundaboutEnc;
    private final boolean skipEmergency;
    private final Set<String> barriers;

    public ModeAccessParser(List<String> restrictionKeys, BooleanEncodedValue accessEnc,
                            boolean skipEmergency, BooleanEncodedValue roundaboutEnc,
                            Set<String> restrictions, Set<String> barriers) {
        this.accessEnc = accessEnc;
        this.roundaboutEnc = roundaboutEnc;
        this.restrictionKeys = restrictionKeys;
        vehicleForward = restrictionKeys.stream().map(r -> r + ":forward").toList();
        vehicleBackward = restrictionKeys.stream().map(r -> r + ":backward").toList();
        ignoreOnewayKeys = restrictionKeys.stream().map(r -> "oneway:" + r).toList();
        restrictedValues = restrictions.isEmpty() ? Set.of("no", "restricted", "military", "emergency") : restrictions;
        this.barriers = barriers.isEmpty() ? CAR_BARRIERS : barriers;
        if (restrictedValues.contains(""))
            throw new IllegalArgumentException("restriction values cannot contain empty string");
        this.skipEmergency = skipEmergency;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null && !FerrySpeedCalculator.isFerry(way))
            return;
        if (skipEmergency && "service".equals(highwayValue) && "emergency_access".equals(way.getTag("service")))
            return;

        Map<String, String> defaults = highwayValue == null ? Map.of() : HIGHWAY_TYPE_DEFAULTS.getOrDefault(highwayValue, UNKNOWN_HIGHWAY_DEFAULT);
        int firstIndex = -1;
        String firstValue = "";
        for (int i = 0; i < restrictionKeys.size(); i++) {
            String key = restrictionKeys.get(i);
            String explicit = way.getTag(key);
            if (explicit != null) {
                firstIndex = i;
                firstValue = explicit;
                break;
            }
            String implied = defaults.get(key);
            if (implied != null) {
                firstIndex = i;
                firstValue = implied;
                break;
            }
        }
        if (isRestricted(firstValue) && !hasPermissiveTemporalRestriction(way, firstIndex, restrictionKeys, INTENDED))
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
                    nodeValue = explicit;
                    break;
                }
                String implied = barrierDefaults.get(key);
                if (implied != null) {
                    nodeValue = implied;
                    break;
                }
            }
            if (restrictedValues.contains(nodeValue))
                return;
            if ("yes".equals(firstNodeTags.get("locked")) && !INTENDED.contains(nodeValue))
                return;
            if (!INTENDED.contains(nodeValue) && barriers.contains(barrierValue))
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

    /**
     * Handles semicolon-separated restriction values like "agricultural;forestry".
     * Returns true (restricted) only if no part is an INTENDED value, and at least one part is restricted.
     */
    private boolean isRestricted(String value) {
        if (value.isEmpty()) return false;
        if (!value.contains(";")) return restrictedValues.contains(value);
        String[] parts = value.split(";");
        for (String part : parts) {
            if (INTENDED.contains(part)) return false;
        }
        for (String part : parts) {
            if (restrictedValues.contains(part)) return true;
        }
        return false;
    }
}
