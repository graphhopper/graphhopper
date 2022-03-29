package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

public enum DrivingSide {
  LEFT("left"), RIGHT("right"), OTHER("other"), MISSING("missing");

  private final String name;

  DrivingSide(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return name;
  }

  public static DrivingSide find(String name) {
    if (Helper.isEmpty(name))
      return MISSING;
    try {
      return DrivingSide.valueOf(Helper.toUpperCase(name));
    } catch (IllegalArgumentException ex) {
      return OTHER;
    }
  }

  public static DrivingSide reverse(DrivingSide drivingSide) {
    switch (drivingSide) {
      case LEFT:
        return RIGHT;
      case RIGHT:
        return LEFT;
      default:
        return MISSING;
    }
  }
}
