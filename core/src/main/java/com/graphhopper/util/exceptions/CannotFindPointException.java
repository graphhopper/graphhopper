package com.graphhopper.util.exceptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an instance of the "Cannot find Point" Exception,
 * whereas the Point that cannot be found is at pointIndex.
 *
 * @author Robin Boldt
 */
public class CannotFindPointException extends GHIllegalArgumentException
{

    private final int pointIndex;

    public CannotFindPointException( String var1, int pointIndex )
    {
        super(var1);
        this.pointIndex = pointIndex;
    }

    public int getPointIndex()
    {
        return this.pointIndex;
    }

    @Override
    public Map<String, String> getIllegalArgumentDetails(){
        Map<String, String> illegalArgumentDetails = new HashMap<>(1);
        illegalArgumentDetails.put("not_found_point_index", String.valueOf(this.pointIndex));
        return illegalArgumentDetails;
    }

}
