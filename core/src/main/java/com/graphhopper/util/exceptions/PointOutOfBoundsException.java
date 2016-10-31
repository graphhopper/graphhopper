package com.graphhopper.util.exceptions;

/**
 * Refinement of the CannotFindPointException that indicates that a point is placed out of the graphs bounds
 *
 * @author Robin Boldt
 */
public class PointOutOfBoundsException extends PointNotFoundException {
    public PointOutOfBoundsException(String var1, int pointIndex) {
        super(var1, pointIndex);
    }
}
