package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.util.countryrules.CountryRule;
import com.graphhopper.routing.ev.Cycleway;
import com.graphhopper.routing.ev.DrivingSide;
import com.graphhopper.storage.IntsRef;

import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;

public class OSMCyclewayParser implements TagParser {

    private final EnumEncodedValue<Cycleway> cyclewayEnc;
    private final Set<String> oneways = new HashSet<>(5);
    protected final HashMap<String, Cycleway> oppositeLanes = new HashMap<>();

    public OSMCyclewayParser() {
        this(new EnumEncodedValue<>(Cycleway.KEY, Cycleway.class, true));
    }

    public OSMCyclewayParser(EnumEncodedValue<Cycleway> cyclewayEnc) {
        this.cyclewayEnc = cyclewayEnc;
        oneways.add("yes");
        oneways.add("true");
        oneways.add("1");
        oneways.add("-1");

        oppositeLanes.put("opposite", Cycleway.SHARED_LANE);
        oppositeLanes.put("opposite_lane", Cycleway.LANE);
        oppositeLanes.put("opposite_track", Cycleway.TRACK);
        oppositeLanes.put("opposite_share_busway", Cycleway.SHARE_BUSWAY);
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> list) {
        list.add(cyclewayEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, IntsRef relationFlags) {
        // Figuring out what kind of bike infra applies to the forward and backward direction
        // is complicated.
        //
        // The goal here is, given the cycleway, cycleway:both:*, and cycleway:left/right:* tags
        // and the driving side, to come up with a notion of forward and backward cycleway values,
        // normalized not to use the "opposite" values.
        //
        // To simplify the number of cases, cycleway:both is treated as a shorthand for setting
        // both cycleway:left and cycleway:right, instead of being treated as a different case.
        // Note that this is different from how the cyclewag tag is treated (see
        // https://wiki.openstreetmap.org/wiki/Key:cycleway:both).

        Cycleway cycleway = Cycleway.find(readerWay.getTag("cycleway"));

        // Default forward and backward to the "cycleway" value until we find a more
        // specific value.
        Cycleway cyclewayForward = cycleway, cyclewayBackward = cycleway;

        // If we find opposite_* tags, normalize them, and treat that as the final word
        // (discounting any contradictory left/right tags which would be a tagging error).
        if (oppositeLanes.containsKey(cycleway)) {
            cyclewayForward = Cycleway.SHARED_LANE;
            cyclewayBackward = oppositeLanes.get(cycleway);
        } else {
            // Driving side is needed to compute default directionality of cycleway:left/right.
            // See defaults described in wiki:
            // https://wiki.openstreetmap.org/wiki/Key:cycleway:right:oneway
            DrivingSide drivingSide = DrivingSide.find(readerWay.getTag("driving_side"));
            if (drivingSide == DrivingSide.MISSING) {
              // Note: This code will run in almost all cases. It's very
              // uncommon for a way to have an exception to the driving side.
              //
              // Note also: country rules have to be turned on in the
              // GraphHopper config file for this to work!
              CountryRule countryRule = readerWay.getTag("country_rule", null);
              if (countryRule != null) {
                  drivingSide = countryRule.getDrivingSide(readerWay, drivingSide);
              }
            }
            boolean isOnewayForCars = readerWay.hasTag("oneway", oneways);

            Cycleway cyclewayLeft = Cycleway.find(
                readerWay.getFirstPriorityTag(Arrays.asList("cycleway:left", "cycleway:both")));
            String cyclewayLeftOnewayTag = readerWay.getTag("cycleway:left:oneway", "");
            boolean cyclewayLeftTwoway = cyclewayLeftOnewayTag.equals("no");
            boolean cyclewayLeftBackward = cyclewayLeftOnewayTag.equals("-1") || (
                cyclewayLeftOnewayTag.equals("") && drivingSide == DrivingSide.RIGHT
                && !isOnewayForCars
            );
            if (cyclewayLeftTwoway || cyclewayLeftBackward)
                cyclewayBackward = mergeCyclewayValues(cyclewayBackward, cyclewayLeft);
            if (cyclewayLeftTwoway || !cyclewayLeftBackward)
                cyclewayForward = mergeCyclewayValues(cyclewayForward, cyclewayLeft);

            Cycleway cyclewayRight = Cycleway.find(
                readerWay.getFirstPriorityTag(Arrays.asList("cycleway:right", "cycleway:both")));
            String cyclewayRightOnewayTag = readerWay.getTag("cycleway:right:oneway", "");
            boolean cyclewayRightTwoway = cyclewayRightOnewayTag.equals("no");
            boolean cyclewayRightBackward = cyclewayRightOnewayTag.equals("-1") || (
                cyclewayRightOnewayTag.equals("") && drivingSide == DrivingSide.LEFT
                && !isOnewayForCars
            );
            if (cyclewayRightTwoway || cyclewayRightBackward)
                cyclewayBackward = mergeCyclewayValues(cyclewayBackward, cyclewayRight);
            if (cyclewayRightTwoway || !cyclewayRightBackward)
                cyclewayForward = mergeCyclewayValues(cyclewayForward, cyclewayRight);
        }

        if (cyclewayForward != Cycleway.MISSING)
            cyclewayEnc.setEnum(false, edgeFlags, cyclewayForward);
        if (cyclewayBackward != Cycleway.MISSING)
            cyclewayEnc.setEnum(true, edgeFlags, cyclewayBackward);

        return edgeFlags;
    }

    private Cycleway mergeCyclewayValues(Cycleway first, Cycleway second) {
        // Merge two cycleway values by the following rules:
        // NO takes precedence over MISSING
        // Any affirmative value takes precedence over NO
        // track > lane > (shared_lane or share_busway)
        // Otherwise second takes precedence over first
        if (second == Cycleway.MISSING)
            return first;
        else if (second == Cycleway.NO && first != Cycleway.MISSING)
            return first;
        else if ((first == Cycleway.TRACK || first == Cycleway.LANE) && (second == Cycleway.SHARED_LANE || second == Cycleway.SHARE_BUSWAY))
            return first;
        else if (first == Cycleway.TRACK && second == Cycleway.LANE)
            return first;
        else
            return second;
    }
}
