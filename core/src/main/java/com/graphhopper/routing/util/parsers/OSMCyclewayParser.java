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

public class OSMCyclewayParser implements TagParser {

  private final EnumEncodedValue<Cycleway> cyclewayEnc;
  private final Set<String> oneways = new HashSet<>(5);
  protected final Set<String> restrictedValues = new HashSet<>(5);

  public OSMCyclewayParser() {
    this(new EnumEncodedValue<>(Cycleway.KEY, Cycleway.class, true));
  }

  public OSMCyclewayParser(EnumEncodedValue<Cycleway> cyclewayEnc) {
    this.cyclewayEnc = cyclewayEnc;
    oneways.add("yes");
    oneways.add("true");
    oneways.add("1");
    oneways.add("-1");
    restrictedValues.add("agricultural");
    restrictedValues.add("forestry");
    restrictedValues.add("no");
    restrictedValues.add("restricted");
    restrictedValues.add("delivery");
    restrictedValues.add("military");
    restrictedValues.add("emergency");
    restrictedValues.add("private");
  }

  @Override
  public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> list) {
    list.add(cyclewayEnc);
  }

  @Override
  public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, IntsRef relationFlags) {
    String cyclewayTag = readerWay
        .getFirstPriorityTag(Arrays.asList("cycleway", "cycleway:both"));
    Cycleway cycleway = Cycleway.find(cyclewayTag);
    
    // Determine the forward cycleway side from country rules
    DrivingSide drivingSide = DrivingSide.find(readerWay.getTag("driving_side"));
    CountryRule countryRule = readerWay.getTag("country_rule", null);
    if (countryRule != null) {
        drivingSide = countryRule.getDrivingSide(readerWay, drivingSide);
    }
    Cycleway cyclewayForward = Cycleway.find(readerWay.getTag("cycleway:" + drivingSide.toString()));
    Cycleway cyclewayBackward = Cycleway.find(readerWay.getTag("cycleway:" + DrivingSide.reverse(drivingSide).toString()));

    if (cycleway != Cycleway.MISSING) {
      cyclewayEnc.setEnum(false, edgeFlags, cycleway);
      cyclewayEnc.setEnum(true, edgeFlags, cycleway);
    }
    if (cyclewayForward != Cycleway.MISSING) {
      cyclewayEnc.setEnum(false, edgeFlags, cyclewayForward);
      if (isOneway(readerWay)) {
        cyclewayEnc.setEnum(true, edgeFlags, cyclewayForward);
      }
    }
    if (cyclewayBackward != Cycleway.MISSING) {
      cyclewayEnc.setEnum(true, edgeFlags, cyclewayBackward);
      if (isOneway(readerWay)) {
        cyclewayEnc.setEnum(false, edgeFlags, cyclewayBackward);
      }
    }

    return edgeFlags;
  }

  protected boolean isOneway(ReaderWay way) {
    return way.hasTag("oneway", oneways)
        || way.hasTag("oneway:bicycle", oneways)
        || way.hasTag("cycleway:left:oneway", oneways)
        || way.hasTag("cycleway:right:oneway", oneways)
        || way.hasTag("vehicle:backward", restrictedValues)
        || way.hasTag("vehicle:forward", restrictedValues)
        || way.hasTag("bicycle:backward", restrictedValues)
        || way.hasTag("bicycle:forward", restrictedValues);
  }
}
