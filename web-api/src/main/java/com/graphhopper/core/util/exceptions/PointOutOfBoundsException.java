package com.graphhopper.core.util.exceptions;

/**
 * Refinement of the CannotFindPointException that indicates that a point is placed out of the graphs bounds
 *
 * @author Robin Boldt
 */
public class PointOutOfBoundsException extends PointNotFoundException {

    private static final long serialVersionUID = 1L;
    
    public PointOutOfBoundsException(String var1, int pointIndex) {
        super(var1, pointIndex);
    }
}
