package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.FerrySpeedCalculator;
import com.graphhopper.storage.IntsRef;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.graphhopper.routing.util.parsers.AccessResolver.ALLOWED;
import static com.graphhopper.routing.util.parsers.AccessResolver.RESTRICTED;

/**
 * Writes the boolean access of a mode: blocking rules, then the AccessResolver, barrier nodes, ferries and the
 * direction.
 */
public class ModeAccessParser implements TagParser {
    private static final Set<String> ONEWAYS_FW = Set.of("yes", "true", "1");

    /**
     * Everything that distinguishes one mode from another. The defaults describe a generic mode without
     * dimensions; a car-like vehicle restricts highways, adds dimensions etc.
     */
    public static class Options {
        public static final Set<String> CAR_HIGHWAYS = Set.of("motorway", "motorway_link", "trunk", "trunk_link",
                "primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link",
                "unclassified", "residential", "living_street", "service", "road", "track", "pedestrian");
        public static final Set<String> CAR_BARRIERS = Set.of("kissing_gate", "fence", "bollard", "stile", "turnstile",
                "cycle_barrier", "motorcycle_barrier", "block", "bus_trap", "sump_buster", "jersey_barrier");
        static final Predicate<ReaderWay> IMPASSABLE = way -> way.hasTag("highway") && (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"));
        static final Predicate<ReaderWay> EMERGENCY_ACCESS = way -> way.hasTag("highway", "service") && way.hasTag("service", "emergency_access");

        final List<String> restrictionKeys;
        Set<String> allowed = ALLOWED;
        Set<String> restricted = RESTRICTED;
        final List<Predicate<ReaderWay>> blocked = new ArrayList<>(List.of(IMPASSABLE, EMERGENCY_ACCESS));
        Set<String> highways;
        Set<String> trackTypes;
        Function<ReaderWay, Map<String, String>> impliedDefaults = AccessResolver::osmDefaults;
        Map<String, Map<String, String>> barrierDefaults = AccessResolver.BARRIER_TYPE_DEFAULTS;
        Set<String> onewayHighways = Set.of("motorway", "motorway_link");
        List<String> directionalKeys;
        List<String> backwardLaneKeys = List.of();
        boolean legacyOneway;
        boolean impliedFerryAccess;
        VehicleConditions conditions;

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
            blocked.remove(EMERGENCY_ACCESS);
            if (skipEmergency) blocked.add(EMERGENCY_ACCESS);
            return this;
        }

        /**
         * @param blocked an additional rule that closes the way regardless of its access values, e.g. explicitOnly
         */
        public Options blocked(Predicate<ReaderWay> blocked) {
            this.blocked.add(blocked);
            return this;
        }

        /**
         * @param highways the accepted highway values, every other highway is blocked, null for all
         */
        public Options highways(Set<String> highways) {
            this.highways = highways;
            return this;
        }

        /**
         * @param trackTypes the accepted tracktype values of highway=track ("" for untagged), other tracks are
         *                   blocked, null for all
         */
        public Options trackTypes(Set<String> trackTypes) {
            this.trackTypes = trackTypes;
            return this;
        }

        List<Predicate<ReaderWay>> blockingRules() {
            List<Predicate<ReaderWay>> rules = new ArrayList<>(blocked);
            if (highways != null)
                rules.add(way -> way.hasTag("highway") && !way.hasTag("highway", highways));
            if (trackTypes != null)
                rules.add(way -> way.hasTag("highway", "track") && !trackTypes.contains(way.getTag("tracktype", "")));
            return rules;
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
         * @param conditions the vehicle dimensions: ways with a lower limit like maxweight are blocked and the
         *                   non-temporal conditionals are evaluated, null for none
         */
        public Options conditions(VehicleConditions conditions) {
            this.conditions = conditions;
            return blocked(way -> way.hasTag("highway") && conditions.exceedsLimits(way));
        }
    }

    private final List<String> vehicleForward;
    private final List<String> vehicleBackward;
    private final List<String> onewayModeKeys;
    private final BooleanEncodedValue accessEnc;
    private final BooleanEncodedValue roundaboutEnc;
    private final AccessResolver resolver;
    private final List<Predicate<ReaderWay>> blockingRules;
    private final Options options;

    public ModeAccessParser(BooleanEncodedValue accessEnc, BooleanEncodedValue roundaboutEnc, Options options) {
        this.accessEnc = accessEnc;
        this.roundaboutEnc = roundaboutEnc;
        this.options = options;
        resolver = new AccessResolver(options.restrictionKeys, options.allowed, options.restricted,
                options.impliedDefaults, options.barrierDefaults, options.conditions);
        blockingRules = options.blockingRules();
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
        return resolver.getRestrictionKeys();
    }

    public BooleanEncodedValue getAccessEnc() {
        return accessEnc;
    }

    @Override
    public String toString() {
        return accessEnc.getName();
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        // don't want platforms and other random stuff here for now
        if (!way.hasTag("highway") && !FerrySpeedCalculator.isFerry(way))
            return;
        for (Predicate<ReaderWay> rule : blockingRules)
            if (rule.test(way))
                return;

        AccessResolver.Result result = resolver.resolve(way);
        if (resolver.isRestricted(way, result))
            return;

        if (way.hasTag("gh:barrier_edge") && way.hasTag("node_tags")) {
            List<Map<String, Object>> nodeTags = way.getTag("node_tags", null);
            if (resolver.isBarrier(nodeTags.get(0)))
                return;
        }

        if (FerrySpeedCalculator.isFerry(way)) {
            if (resolver.isAllowed(result.value())
                    // implied default is allowed only if foot and bicycle is not specified:
                    || options.impliedFerryAccess && result.value().isEmpty() && !way.hasTag("foot") && !way.hasTag("bicycle")
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
            boolean forwardClosed = way.hasTag("oneway", "-1") || way.hasTag(vehicleForward, options.restricted);
            boolean oneway = forwardClosed || isRoundabout || way.hasTag("oneway", ONEWAYS_FW) || way.hasTag(vehicleBackward, options.restricted);
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
        if (resolver.isRestricted(way.getFirstValue(vehicleForward))) return false;
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
        if (resolver.isRestricted(way.getFirstValue(vehicleBackward))) return false;
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
