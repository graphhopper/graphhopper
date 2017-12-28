package com.graphhopper.util;

/**
 * Represents a single lane, used for the instructions.
 *
 * @see com.graphhopper.routing.util.CarFlagEncoder
 */
public class Lane {

    /**
     * Direction as string based on the OSM lane tags.
     */
    public final String direction;

    /**
     * Internal code that corresponds to the lane tags.
     */
    public final int directionCode;

    /**
     * Defines if the lane can be used for the next maneuver.
     */
    public boolean valid;

    public Lane(String direction, int directionCode) {
        this.directionCode = directionCode;
        this.direction = direction;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getDirection() {
        return direction;
    }

    public boolean isValid() {
        return valid;
    }

    public int getDirectionCode() {
        return directionCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("direction: ");
        sb.append(direction).append(", ");
        sb.append("directionCode: ");
        sb.append(directionCode).append(", ");
        sb.append("valid: ");
        sb.append(valid);
        return sb.toString();
    }
}