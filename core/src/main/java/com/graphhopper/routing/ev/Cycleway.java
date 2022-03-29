package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

public enum Cycleway {

  /**
   * This enum defines the cycling infrastructure that is an inherent part of the
   * road.
   * It is heavily influenced from the cycleway tag in OSM.
   * All edges that do not fit get OTHER as value.
   */
  MISSING("missing"),
  ASL("asl"),
  CROSSING("crossing"),
  LANE("lane"), OPPOSITE_LANE("opposite_lane"),
  NO("no"), YES("yes"),
  OPPOSITE("opposite"),
  SEPARATE("separate"),
  SHARE_BUSWAY("share_busway"), OPPOSITE_SHARE_BUSWAY("opposite_share_busway"),
  SHARED_LANE("shared_lane"),
  SHARED("shared"),
  SHOULDER("shoulder"),
  TRACK("track"), OPPOSITE_TRACK("opposite_track"),
  RIGHT("right"), LEFT("left"), BOTH("both"),
  SIDEPATH("sidepath"),
  OTHER("other");

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
    if (Helper.isEmpty(name))
      return MISSING;
    try {
      return Cycleway.valueOf(Helper.toUpperCase(name));
    } catch (IllegalArgumentException ex) {
      return OTHER;
    }
  }
}
