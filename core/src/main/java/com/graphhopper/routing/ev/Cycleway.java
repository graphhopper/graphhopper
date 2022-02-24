package com.graphhopper.routing.ev;

public enum Cycleway {

  /**
   * This enum defines the cycling infrastructure that is an inherent part of the
   * road.
   * It is heavily influenced from the cycleway tag in OSM.
   * All edges that do not fit get OTHER as value.
   */
  OTHER("other"),
  ASL("asl"),
  CROSSING("crossing"),
  LANE("lane"), OPPOSITE_LANE("opposite_lane"),
  NO("no"),
  OPPOSITE("opposite"),
  SEPARATE("separate"),
  SHARE_BUSWAY("share_busway"), OPPOSITE_SHARE_BUSWAY("opposite_share_busway"),
  SHARED_LANE("shared_lane"),
  SHARED("shared"),
  SHOULDER("shoulder"),
  TRACK("track"), OPPOSITE_TRACK("opposite_track");

  public static final String KEY = "cycleway";

  private final String name;

  Cycleway(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return name;
  }

  public static Cycleway find(String name) {
    if (name == null || name.isEmpty())
      return OTHER;

    for (Cycleway cycleway : values()) {
      if (cycleway.name().equalsIgnoreCase(name)) {
        return cycleway;
      }
    }

    return OTHER;
  }
}
