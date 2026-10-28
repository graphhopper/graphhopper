package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.storage.IntsRef;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.graphhopper.routing.util.parsers.OSMTemporalAccessParser.hasPermissiveTemporalRestriction;

public class ModeAccessParser implements TagParser {

    public static final Set<String> ALLOWED = Set.of("yes", "designated", "official", "permissive", "destination");
    public static final Set<String> RESTRICTED = Set.of("no", "restricted", "military", "emergency",
            "private", "permit", "service", "delivery", "customers", "agricultural", "forestry");
    // a locked barrier is passable only with these values, not with destination
    private static final Set<String> INTENDED = Set.of("yes", "designated", "official", "permissive");
    private static final Map<String, String> MOTORROAD_DEFAULTS = Map.of("foot", "no", "bicycle", "no");
    static final Map<String, Map<String, String>> HIGHWAY_TYPE_DEFAULTS;
    static final Map<String, Map<String, String>> BARRIER_TYPE_DEFAULTS;

    static {
        // https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Access_restrictions
        Map<String, Map<String, String>> m = new HashMap<>();
        // For implied bicycle defaults we use "dismount" rather than "no" on highways where
        // pushing a bike is physically reasonable (pedestrian streets, footways, stairs, indoor
        // corridors, station platforms). Our routing layer treats DISMOUNT as "can push" —
        // usable but qualified — whereas NO is treated as a hard block. motorway/motorway_link
        // keep "no" because bikes are legally prohibited even on foot. Explicit bicycle=no tags
        // on a way still resolve to NO; only the implicit highway-type defaults become DISMOUNT.
        m.put("motorway", Map.of("motor_vehicle", "designated", "foot", "no", "bicycle", "no"));
        m.put("motorway_link", Map.of("motor_vehicle", "designated", "foot", "no", "bicycle", "no"));
        m.put("steps", Map.of("motor_vehicle", "no", "bicycle", "dismount", "foot", "designated"));
        m.put("footway", Map.of("motor_vehicle", "no", "bicycle", "dismount", "foot", "designated"));
        m.put("cycleway", Map.of("motor_vehicle", "no", "bicycle", "designated", "foot", "no"));
        m.put("pedestrian", Map.of("motor_vehicle", "no", "bicycle", "dismount", "foot", "designated"));
        m.put("path", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        m.put("bridleway", Map.of("motor_vehicle", "no", "foot", "yes", "bicycle", "yes"));
        m.put("busway", Map.of("access", "no", "bus", "designated"));
        m.put("construction", Map.of("access", "no"));
        m.put("proposed", Map.of("access", "no"));
        m.put("raceway", Map.of("access", "no"));
        // corridor stays bicycle=no (not "dismount") because BikeCommonAccessParser doesn't list
        // it in allowedHighways — bike_access is already false there. Marking road_access as
        // DISMOUNT would suggest "can push" while access says "can't use it at all".
        m.put("corridor", Map.of("motor_vehicle", "no", "bicycle", "no", "foot", "yes"));
        m.put("platform", Map.of("motor_vehicle", "no", "bicycle", "dismount", "foot", "yes"));
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

    /**
     * Everything that distinguishes one mode from another. The defaults describe a generic mode without
     * dimensions; a car-like vehicle restricts highways, adds dimensions etc.
     */
    public static class Options {
        final List<String> restrictionKeys;
        Set<String> allowed = ALLOWED;
        Set<String> restricted = RESTRICTED;
        boolean skipEmergency = true;
        Set<String> highways;
        Set<String> trackTypes;
        Function<ReaderWay, Map<String, String>> impliedDefaults = ModeAccessParser::osmDefaults;
        Map<String, Map<String, String>> barrierDefaults = BARRIER_TYPE_DEFAULTS;
        Predicate<ReaderWay> blocked = way -> false;
        Set<String> onewayHighways = Set.of("motorway", "motorway_link");
        List<String> directionalKeys;
        List<String> backwardLaneKeys = List.of();
        boolean legacyOneway;
        boolean impliedFerryAccess;
        VehicleConditions conditions;

        public static final Set<String> CAR_HIGHWAYS = Set.of("motorway", "motorway_link", "trunk", "trunk_link",
                "primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link",
                "unclassified", "residential", "living_street", "service", "road", "track", "pedestrian");
        public static final Set<String> CAR_BARRIERS = Set.of("kissing_gate", "fence", "bollard", "stile", "turnstile",
                "cycle_barrier", "motorcycle_barrier", "block", "bus_trap", "sump_buster", "jersey_barrier");

        /**
         * A motor vehicle like the former CarAccessParser: highway allowlist, tracks only grade1, grade2 or
         * untagged, pedestrian only if explicitly allowed, no oneway implied by the highway type, customers unknown
         */
        public static Options motorVehicle(List<String> restrictionKeys) {
            Set<String> restricted = new HashSet<>(RESTRICTED);
            restricted.remove("customers");
            return new Options(restrictionKeys).
                    values(ALLOWED, restricted).
                    highways(CAR_HIGHWAYS).
                    trackTypes(Set.of("grade1", "grade2", "")).
                    impliedDefaults(way -> way.hasTag("highway", "pedestrian") ? Map.of("access", "no") : Map.of()).
                    barrierDefaults(barriers(CAR_BARRIERS)).
                    onewayHighways(Set.of()).
                    directionalKeys(List.of("motor_vehicle", "vehicle")).
                    legacyOneway(true).
                    impliedFerryAccess(true);
        }

        /**
         * @return barrier defaults that block every mode unless a restriction key allows it
         */
        public static Map<String, Map<String, String>> barriers(Set<String> barriers) {
            Map<String, Map<String, String>> result = new HashMap<>();
            for (String barrier : barriers) result.put(barrier, Map.of("access", "no"));
            return result;
        }

        /**
         * @return a rule that blocks the given highways, motorroad=yes and a minspeed above maxMinSpeed unless the
         * given key has one of the allowed values, e.g. moped=yes for a scooter
         */
        public static Predicate<ReaderWay> explicitOnly(String key, Set<String> allowed, Set<String> highways, double maxMinSpeed) {
            return way -> !way.hasTag(key, allowed) && (way.hasTag("highway", highways) || way.hasTag("motorroad", "yes")
                    || way.hasTag("minspeed") && OSMMaxSpeedParser.parseMaxspeedString(way.getTag("minspeed")) > maxMinSpeed);
        }

        public Options(List<String> restrictionKeys) {
            this.restrictionKeys = restrictionKeys;
            directionalKeys = restrictionKeys;
            impliedFerryAccess = restrictionKeys.contains("motorcar");
        }

        /**
         * Moves the given values from restricted to allowed (allow) or adds them to restricted (restrict).
         */
        public Options allow(Set<String> allow, Set<String> restrict) {
            Set<String> allowed = new HashSet<>(this.allowed), restricted = new HashSet<>(this.restricted);
            for (String value : allow) {
                if (restricted.remove(value))
                    allowed.add(value);
                else if (!allowed.contains(value))
                    throw new IllegalArgumentException("cannot allow '" + value + "' — not a known restricted value");
            }
            for (String value : restrict) {
                if (allowed.contains(value))
                    throw new IllegalArgumentException("cannot restrict '" + value + "' — it is an allowed value");
                restricted.add(value);
            }
            return values(allowed, restricted);
        }

        public Options values(Set<String> allowed, Set<String> restricted) {
            this.allowed = Set.copyOf(allowed);
            this.restricted = Set.copyOf(restricted);
            return this;
        }

        public Set<String> getAllowed() {
            return allowed;
        }

        public Set<String> getRestricted() {
            return restricted;
        }

        public Options skipEmergency(boolean skipEmergency) {
            this.skipEmergency = skipEmergency;
            return this;
        }

        /**
         * @param highways the accepted highway values, null for all
         */
        public Options highways(Set<String> highways) {
            this.highways = highways;
            return this;
        }

        /**
         * @param trackTypes the accepted tracktype values of highway=track ("" for untagged), null for all
         */
        public Options trackTypes(Set<String> trackTypes) {
            this.trackTypes = trackTypes;
            return this;
        }

        /**
         * @param impliedDefaults the access values implied by the way tags, per restriction key
         */
        public Options impliedDefaults(Function<ReaderWay, Map<String, String>> impliedDefaults) {
            this.impliedDefaults = impliedDefaults;
            return this;
        }

        /**
         * @param barrierDefaults the access values implied by a barrier node, per barrier value and restriction key
         */
        public Options barrierDefaults(Map<String, Map<String, String>> barrierDefaults) {
            this.barrierDefaults = barrierDefaults;
            return this;
        }

        /**
         * @param blocked a rule beyond the access values, e.g. explicitOnly
         */
        public Options blocked(Predicate<ReaderWay> blocked) {
            this.blocked = blocked;
            return this;
        }

        /**
         * @param onewayHighways the highway values that imply oneway=yes
         */
        public Options onewayHighways(Set<String> onewayHighways) {
            this.onewayHighways = onewayHighways;
            return this;
        }

        /**
         * @param directionalKeys the keys of the considered oneway:key, key:forward and key:backward tags, most specific first
         */
        public Options directionalKeys(List<String> directionalKeys) {
            this.directionalKeys = directionalKeys;
            return this;
        }

        /**
         * @param backwardLaneKeys the keys like psv for which a lane against the oneway opens the backward direction,
         *                         e.g. oneway:psv=no, psv=opposite_lane, lanes:psv:backward=1 or psv:backward=yes
         */
        public Options backwardLaneKeys(List<String> backwardLaneKeys) {
            this.backwardLaneKeys = backwardLaneKeys;
            return this;
        }

        /**
         * @param legacyOneway true for the oneway handling of the former CarAccessParser: a restricted key:forward
         *                     closes the forward and opens the backward direction even for oneway=yes, a roundabout
         *                     is oneway even with oneway=no, oneway:key is ignored
         */
        public Options legacyOneway(boolean legacyOneway) {
            this.legacyOneway = legacyOneway;
            return this;
        }

        /**
         * @param impliedFerryAccess true if a ferry without access tags is accessible (unless foot or bicycle is
         *                           tagged) and hgv=yes implies access, default: true if motorcar is a restriction key
         */
        public Options impliedFerryAccess(boolean impliedFerryAccess) {
            this.impliedFerryAccess = impliedFerryAccess;
            return this;
        }

        /**
         * @param conditions the vehicle dimensions for limits like maxweight and the non-temporal conditionals, null for none
         */
        public Options conditions(VehicleConditions conditions) {
            this.conditions = conditions;
            return this;
        }
    }

    private final Set<String> allowed;
    private final Set<String> restricted;
    private final List<String> restrictionKeys;
    private final List<String> vehicleForward;
    private final List<String> vehicleBackward;
    private final List<String> onewayModeKeys;
    private final BooleanEncodedValue accessEnc;
    private final BooleanEncodedValue roundaboutEnc;
    private final Options options;

    public ModeAccessParser(BooleanEncodedValue accessEnc, BooleanEncodedValue roundaboutEnc, Options options) {
        this.accessEnc = accessEnc;
        this.roundaboutEnc = roundaboutEnc;
        this.options = options;
        this.restrictionKeys = options.restrictionKeys;
        this.allowed = options.allowed;
        this.restricted = options.restricted;
        vehicleForward = options.directionalKeys.stream().map(r -> r + ":forward").toList();
        vehicleBackward = options.directionalKeys.stream().map(r -> r + ":backward").toList();
        onewayModeKeys = options.directionalKeys.stream().map(r -> "oneway:" + r).toList();
    }

    public ModeAccessParser(List<String> restrictionKeys, BooleanEncodedValue accessEnc,
                            boolean skipEmergency, BooleanEncodedValue roundaboutEnc,
                            Set<String> allow, Set<String> restrict) {
        this(accessEnc, roundaboutEnc, new Options(restrictionKeys).skipEmergency(skipEmergency).allow(allow, restrict));
    }

    public ModeAccessParser(List<String> restrictionKeys, BooleanEncodedValue accessEnc,
                            boolean skipEmergency, BooleanEncodedValue roundaboutEnc) {
        this(restrictionKeys, accessEnc, skipEmergency, roundaboutEnc, Set.of(), Set.of());
    }

    public List<String> getRestrictionKeys() {
        return restrictionKeys;
    }

    public BooleanEncodedValue getAccessEnc() {
        return accessEnc;
    }

    @Override
    public String toString() {
        return accessEnc.getName();
    }

    /**
     * The access values implied by the highway type and motorroad=yes
     */
    public static Map<String, String> osmDefaults(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        Map<String, String> defaults = highwayValue == null ? Map.of() : HIGHWAY_TYPE_DEFAULTS.getOrDefault(highwayValue, Map.of());
        // motorroad=yes is an annoying special case: it's not a highway=* value but a separate tag
        // that implies foot=no, bicycle=no. If we find more tags like this, we'll need a more
        // general mechanism for non-highway implied defaults.
        if (way.hasTag("motorroad", "yes")) {
            Map<String, String> merged = new HashMap<>(defaults);
            MOTORROAD_DEFAULTS.forEach(merged::putIfAbsent);
            return merged;
        }
        return defaults;
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
        if (options.skipEmergency && "service".equals(highwayValue) && "emergency_access".equals(way.getTag("service")))
            return;
        if (highwayValue != null) {
            if (options.highways != null && !options.highways.contains(highwayValue))
                return;
            if (options.trackTypes != null && "track".equals(highwayValue) && !options.trackTypes.contains(way.getTag("tracktype", "")))
                return;
            if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
                return;
        }
        if (options.blocked.test(way))
            return;

        Map<String, String> defaults = options.impliedDefaults.apply(way);
        int firstIndex = -1;
        String firstValue = "";
        for (int i = 0; i < restrictionKeys.size(); i++) {
            String key = restrictionKeys.get(i);
            // a conditional of the same key beats its plain value, e.g. hgv:conditional=no @ (weight > 7.5) and hgv=yes
            String conditional = options.conditions == null ? null : options.conditions.conditional(way, key);
            // none @ destination lifts a restriction like yes
            if ("none".equals(conditional)) conditional = "yes";
            if (conditional != null && (allowed.contains(conditional) || restricted.contains(conditional))) {
                firstIndex = i;
                firstValue = conditional;
                break;
            }
            String explicit = recognized(way.getTag(key));
            if (explicit != null) {
                firstIndex = i;
                firstValue = explicit;
                break;
            }
            // unknown value — fall through to implied default for same key, then keep looking
            String implied = defaults.get(key);
            if (implied != null) {
                firstIndex = i;
                firstValue = implied;
                break;
            }
        }
        if (restricted.contains(firstValue) && !hasPermissiveTemporalRestriction(way, firstIndex, restrictionKeys, allowed))
            return;

        if (highwayValue != null && options.conditions != null && options.conditions.exceedsLimits(way))
            return;

        if (way.hasTag("gh:barrier_edge") && way.hasTag("node_tags")) {
            List<Map<String, Object>> nodeTags = way.getTag("node_tags", null);
            Map<String, Object> firstNodeTags = nodeTags.get(0);
            String barrierValue = firstNodeTags.containsKey("barrier") ? (String) firstNodeTags.get("barrier") : "";
            Map<String, String> barrierDefaults = options.barrierDefaults.getOrDefault(barrierValue, Map.of());
            // Walk restriction keys checking explicit node tags and barrier type defaults. Unlike for ways an
            // unknown explicit value hides the less specific explicit values, only the defaults are still considered.
            String nodeValue = "";
            boolean unknown = false;
            for (String key : restrictionKeys) {
                String explicit = unknown ? null : (String) firstNodeTags.get(key);
                if (explicit != null) {
                    if (allowed.contains(explicit) || restricted.contains(explicit)) {
                        nodeValue = explicit;
                        break;
                    }
                    unknown = true;
                }
                String implied = barrierDefaults.get(key);
                if (implied != null) {
                    nodeValue = implied;
                    break;
                }
            }
            if (restricted.contains(nodeValue))
                return;
            if ("yes".equals(firstNodeTags.get("locked")) && !INTENDED.contains(nodeValue))
                return;
        }

        if (FerrySpeedCalculator.isFerry(way)) {
            if (allowed.contains(firstValue)
                    // implied default is allowed only if foot and bicycle is not specified:
                    || options.impliedFerryAccess && firstValue.isEmpty() && !way.hasTag("foot") && !way.hasTag("bicycle")
                    // if hgv is allowed then smaller trucks and cars are allowed too even if not specified
                    || options.impliedFerryAccess && way.hasTag("hgv", "yes")) {
                // ferry is allowed via explicit tag
            } else {
                return;
            }
        }

        boolean isRoundabout = roundaboutEnc.getBool(false, edgeId, edgeIntAccess);
        boolean forward, backward;
        if (options.legacyOneway) {
            boolean forwardClosed = way.hasTag("oneway", "-1") || way.hasTag(vehicleForward, restricted);
            boolean oneway = forwardClosed || isRoundabout || way.hasTag("oneway", ONEWAYS_FW) || way.hasTag(vehicleBackward, restricted);
            forward = !forwardClosed;
            backward = !oneway || forwardClosed || hasBackwardLane(way);
        } else {
            forward = isForwardAccessible(way);
            backward = isBackwardAccessible(way, isRoundabout);
        }
        if (forward)
            accessEnc.setBool(false, edgeId, edgeIntAccess, true);
        if (backward)
            accessEnc.setBool(true, edgeId, edgeIntAccess, true);
    }

    /**
     * @return the allowed or restricted value of a tag like "yes;forestry" (allowed wins), null if unknown
     */
    private String recognized(String value) {
        if (value == null) return null;
        if (allowed.contains(value)) return value;
        if (restricted.contains(value)) return value;
        if (value.indexOf(';') < 0) return null;
        String result = null;
        for (String raw : value.split(";")) {
            String v = raw.trim();
            if (allowed.contains(v)) return v;
            if (restricted.contains(v)) result = v;
        }
        return result;
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
        if (restricted.contains(way.getFirstValue(vehicleForward))) return false;
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
        if (hasBackwardLane(way)) return true;
        // 2. mode-specific directional prohibition: <mode>:backward=no
        if (restricted.contains(way.getFirstValue(vehicleBackward))) return false;
        // 3. generic oneway tag, then implied oneway from highway type / roundabout.
        //    oneway=no explicitly relaxes the motorway/roundabout implication.
        if (way.hasTag("oneway", ONEWAYS_FW)) return false;
        if (way.hasTag("oneway", "no")) return true;
        if (way.hasTag("highway", options.onewayHighways)) return false;
        return !isRoundabout;
    }

    private boolean hasBackwardLane(ReaderWay way) {
        for (String key : options.backwardLaneKeys) {
            // lanes:psv:backward can contain a positive number
            if (way.hasTag("oneway:" + key, "no") || way.hasTag(key, "opposite_lane") || way.hasTag("lanes:" + key + ":backward")
                    || way.hasTag(key + ":lanes:backward") || way.hasTag(key + ":backward", "yes"))
                return true;
        }
        return false;
    }
}
