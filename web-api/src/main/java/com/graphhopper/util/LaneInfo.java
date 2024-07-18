package com.graphhopper.util;

import java.util.List;

public class LaneInfo {
    /**
     * Direction as string based on the OSM lane tags.
     */
    public final List<String> directions;

    /**
     * Defines if the lane can be used for the next turn instruction.
     */
    public boolean valid;

    public LaneInfo(List<String> directions) {
        if (directions == null) throw new IllegalArgumentException("direction cannot be null");
        this.directions = directions;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<String> getDirections() {
        return directions;
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public String toString() {
        return "{directions:" + directions + ", " + "valid:" + valid + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LaneInfo laneInfo = (LaneInfo) o;

        if (valid != laneInfo.valid) return false;
        return directions.equals(laneInfo.directions);
    }

    @Override
    public int hashCode() {
        int result = directions.hashCode();
        result = 31 * result + (valid ? 1 : 0);
        return result;
    }
}
