package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Cycleway;
import com.graphhopper.storage.IntsRef;

import java.util.List;
import java.util.Arrays;

public class OSMCyclewayParser implements TagParser {

  private final EnumEncodedValue<Cycleway> cyclewayEnc;

  public OSMCyclewayParser() {
    this(new EnumEncodedValue<>(Cycleway.KEY, Cycleway.class));
  }

  public OSMCyclewayParser(EnumEncodedValue<Cycleway> cyclewayEnc) {
    this.cyclewayEnc = cyclewayEnc;
  }

  @Override
  public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> list) {
    list.add(cyclewayEnc);
  }

  @Override
  public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, IntsRef relationFlags) {
    String cyclewayTag = readerWay
        .getFirstPriorityTag(Arrays.asList("cycleway", "cycleway:both", "cycleway:left", "cycleway:right"));
    Cycleway cycleway = Cycleway.find(cyclewayTag);
    if (cycleway == Cycleway.MISSING)
      return edgeFlags;

    cyclewayEnc.setEnum(false, edgeFlags, cycleway);
    return edgeFlags;
  }
}
