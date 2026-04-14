package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.storage.IntsRef;

import java.util.*;

import static com.graphhopper.routing.util.parsers.OSMTemporalAccessParser.hasPermissiveTemporalRestriction;

public class ModeAccessParser implements TagParser {

    private static final Set<String> INTENDED = Set.of("yes", "designated", "official", "permissive", "destination");
    private static final Set<String> RESTRICTED = Set.of("no", "restricted", "military", "emergency",
            "private", "permit", "service", "delivery", "customers", "agricultural", "forestry");
    private static final Map<String, String> MOTORROAD_DEFAULTS = Map.of("foot", "no", "bicycle", "no");
    static final Map<String, Map<String, String>> HIGHWAY_TYPE_DEFAULTS;
    static final Map<String, Map<String, String>> BARRIER_TYPE_DEFAULTS;

    static {
        // https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Access_restrictions
        Map<String, Map<String, String>> m = new HashMap<>();
        m.put("motorway", Map.of("motor_vehicle", "designated", "foot", "no", "bicycle", "no"));
        m.put("motorway_link", Map.of("motor_vehicle", "designated", "foot", "no", "bicycle", "no"));
        m.put("steps", Map.of("motor_vehicle", "no", "bicycle", "no", "foot", "designated"));
        m.put("footway", Map.of("motor_vehicle", "no", "bicycle", "no", "foot", "designated"));
        m.put("cycleway", Map.of("motor_vehicle", "no", "bicycle", "designated", "foot", "no"));
        m.put("pedestrian", Map.of("motor_vehicle", "no", "bicycle", "no", "foot", "designated"));
        m.put("path", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        m.put("bridleway", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "no"));
        m.put("busway", Map.of("access", "no", "bus", "designated"));
        m.put("construction", Map.of("access", "no"));
        m.put("proposed", Map.of("access", "no"));
        m.put("raceway", Map.of("access", "no"));
        m.put("corridor", Map.of("motor_vehicle", "no", "bicycle", "no", "foot", "yes"));
        m.put("platform", Map.of("motor_vehicle", "no", "bicycle", "no", "foot", "yes"));
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
    private final Set<String> intended;
    private final Set<String> restricted;
    private final List<String> restrictionKeys;
    private final List<String> vehicleForward;
    private final List<String> vehicleBackward;
    private final List<String> onewayModeKeys;
    private final BooleanEncodedValue accessEnc;
    private final BooleanEncodedValue roundaboutEnc;
    private final boolean skipEmergency;

    public ModeAccessParser(List<String> restrictionKeys, BooleanEncodedValue accessEnc,
                            boolean skipEmergency, BooleanEncodedValue roundaboutEnc,
                            Set<String> allow, Set<String> restrict) {
        this.accessEnc = accessEnc;
        this.roundaboutEnc = roundaboutEnc;
        this.restrictionKeys = restrictionKeys;
        vehicleForward = restrictionKeys.stream().map(r -> r + ":forward").toList();
        vehicleBackward = restrictionKeys.stream().map(r -> r + ":backward").toList();
        onewayModeKeys = restrictionKeys.stream().map(r -> "oneway:" + r).toList();
        this.skipEmergency = skipEmergency;

        this.intended = new HashSet<>(INTENDED);
        this.restricted = new HashSet<>(RESTRICTED);
        for (String value : allow) {
            if (restricted.remove(value))
                intended.add(value);
            else if (!intended.contains(value))
                throw new IllegalArgumentException("cannot allow '" + value + "' — not a known restricted value");
        }
        for (String value : restrict) {
            if (intended.contains(value))
                throw new IllegalArgumentException("cannot restrict '" + value + "' — it is an intended value");
            restricted.add(value);
        }
    }

    public ModeAccessParser(List<String> restrictionKeys, BooleanEncodedValue accessEnc,
                            boolean skipEmergency, BooleanEncodedValue roundaboutEnc) {
        this(restrictionKeys, accessEnc, skipEmergency, roundaboutEnc, Set.of(), Set.of());
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String highwayValue = way.getTag("highway");
        if (highwayValue != null || FerrySpeedCalculator.isFerry(way)) {
            handleHighwayAndFerryTags(edgeId, edgeIntAccess, way, highwayValue);
        }
        // don't want platforms and other random stuff here for now
    }

    private void handleHighwayAndFerryTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, String highwayValue) {
        if (skipEmergency && "service".equals(highwayValue) && "emergency_access".equals(way.getTag("service")))
            return;

        Map<String, String> defaults = highwayValue == null ? Map.of() : HIGHWAY_TYPE_DEFAULTS.getOrDefault(highwayValue, Map.of());
        // motorroad=yes is an annoying special case: it's not a highway=* value but a separate tag
        // that implies foot=no, bicycle=no. If we find more tags like this, we'll need a more
        // general mechanism for non-highway implied defaults.
        if (way.hasTag("motorroad", "yes")) {
            Map<String, String> merged = new HashMap<>(defaults);
            MOTORROAD_DEFAULTS.forEach(merged::putIfAbsent);
            defaults = merged;
        }
        int firstIndex = -1;
        String firstValue = "";
        for (int i = 0; i < restrictionKeys.size(); i++) {
            String key = restrictionKeys.get(i);
            String explicit = way.getTag(key);
            if (explicit != null) {
                if (intended.contains(explicit) || restricted.contains(explicit)) {
                    firstIndex = i;
                    firstValue = explicit;
                    break;
                }
                // unknown value — fall through to implied default for same key, then keep looking
            }
            String implied = defaults.get(key);
            if (implied != null) {
                firstIndex = i;
                firstValue = implied;
                break;
            }
        }
        if (restricted.contains(firstValue) && !hasPermissiveTemporalRestriction(way, firstIndex, restrictionKeys, intended))
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
                    if (intended.contains(explicit) || restricted.contains(explicit)) {
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
            if (restricted.contains(nodeValue))
                return;
            if ("yes".equals(firstNodeTags.get("locked")) && !intended.contains(nodeValue))
                return;
        }

        if (FerrySpeedCalculator.isFerry(way)) {
            boolean isCar = restrictionKeys.contains("motorcar");
            if (intended.contains(firstValue)
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
        if (isForwardAccessible(way))
            accessEnc.setBool(false, edgeId, edgeIntAccess, true);
        if (isBackwardAccessible(way, isRoundabout))
            accessEnc.setBool(true, edgeId, edgeIntAccess, true);
    }

    /**
     * The two directions are computed independently so that blockings from different
     * tag families stack: e.g. oneway=yes closing backward plus bus:forward=no closing
     * forward leaves a bus with nowhere to go, rather than being treated as a single
     * backward-oneway by the dominant rule.
     * <p>
     * Within each direction, rules are evaluated top-to-bottom; the first rule that
     * matches wins. More-specific tags come first, so a mode-specific tag always beats
     * a generic one.
     */
    private boolean isForwardAccessible(ReaderWay way) {
        String modeOneway = way.getFirstValue(onewayModeKeys);
        // 1. explicit per-mode oneway override: oneway:<mode>=yes/-1/no
        if ("no".equals(modeOneway)) return true;
        if (ONEWAYS_FW.contains(modeOneway)) return true;
        if ("-1".equals(modeOneway)) return false;
        // 2. mode-specific directional prohibition: <mode>:forward=no
        if ("no".equals(way.getFirstValue(vehicleForward))) return false;
        // 3. generic oneway tag (forward direction is only closed by oneway=-1;
        //    motorway/roundabout imply forward-oneway, which leaves forward open)
        return !way.hasTag("oneway", "-1");
    }

    private boolean isBackwardAccessible(ReaderWay way, boolean isRoundabout) {
        String modeOneway = way.getFirstValue(onewayModeKeys);
        // 1. explicit per-mode oneway override
        if ("no".equals(modeOneway)) return true;
        if (ONEWAYS_FW.contains(modeOneway)) return false;
        if ("-1".equals(modeOneway)) return true;
        // 2. mode-specific directional prohibition: <mode>:backward=no
        if ("no".equals(way.getFirstValue(vehicleBackward))) return false;
        // 3. generic oneway tag, then implied oneway from highway type / roundabout.
        //    oneway=no explicitly relaxes the motorway/roundabout implication.
        if (way.hasTag("oneway", ONEWAYS_FW)) return false;
        if (way.hasTag("oneway", "no")) return true;
        if (way.hasTag("highway", "motorway", "motorway_link")) return false;
        return !isRoundabout;
    }
}
