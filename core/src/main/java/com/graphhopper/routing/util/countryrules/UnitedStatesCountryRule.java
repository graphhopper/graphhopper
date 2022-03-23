package com.graphhopper.routing.util.countryrules;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DrivingSide;

public class UnitedStatesCountryRule implements CountryRule {
  
  @Override
  public DrivingSide getDrivingSide(ReaderWay readerWay, DrivingSide currentDrivingSide) {
    if (currentDrivingSide != DrivingSide.MISSING) {
      return currentDrivingSide;
    }

    return DrivingSide.RIGHT;
  }
}
