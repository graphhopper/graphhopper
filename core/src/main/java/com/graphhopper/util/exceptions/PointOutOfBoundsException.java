package com.graphhopper.util.exceptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Refinement of the CannotFindPointException that indicates that a point is placed out of the graphs bounds
 *
 * @author Robin Boldt
 */
public class PointOutOfBoundsException extends CannotFindPointException
{
    public PointOutOfBoundsException( String var1, int pointIndex )
    {
        super(var1, pointIndex);
    }
}
