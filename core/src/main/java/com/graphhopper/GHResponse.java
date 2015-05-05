/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper;

import java.util.ArrayList;
import java.util.List;

import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;

/**
 * Wrapper to simplify output of GraphHopper.
 * <p/>
 * @author Peter Karich
 */
public class GHResponse
{
    private String debugInfo = "";
    private final List<Throwable> errors = new ArrayList<Throwable>(4);
    private PointList list = PointList.EMPTY;
    private double distance;
    private double routeWeight;
    private long time;
    private InstructionList instructions = null;
    private boolean found;

    public GHResponse()
    {
    }

    public String getDebugInfo()
    {
        check("getDebugInfo");
        return debugInfo;
    }
    public GHResponse setFound( boolean found )
    {
        this.found = found;
        return this;
    }
    public boolean isFound()
    {
        return found;
    }

    public GHResponse setDebugInfo( String debugInfo )
    {
        if (debugInfo != null)
            this.debugInfo = debugInfo;
        return this;
    }

    private void check( String method )
    {
        if (hasErrors())
        {
            throw new RuntimeException("You cannot call " + method + " if response contains errors. Check this with ghResponse.hasErrors(). "
                    + "Errors are: " + getErrors());
        }
    }

    /**
     * @return true if one or more error found
     */
    public boolean hasErrors()
    {
        return !errors.isEmpty();
    }

    public List<Throwable> getErrors()
    {
        return errors;
    }

    @SuppressWarnings("unchecked")
    public GHResponse addError( Throwable error )
    {
        errors.add(error);
        return this;
    }

    public GHResponse setPoints( PointList points )
    {
        list = points;
        return this;
    }

    /**
     * This method returns all points on the path. Keep in mind that calculating the distance from
     * these point might yield different results compared to getDistance as points could have been
     * simplified on import or after querying.
     */
    public PointList getPoints()
    {
        check("getPoints");
        return list;
    }

    public GHResponse setDistance( double distance )
    {
        this.distance = distance;
        return this;
    }

    /**
     * This method returns the distance of the path. Always prefer this method over
     * getPoints().calcDistance
     * <p>
     * @return distance in meter
     */
    public double getDistance()
    {
        check("getDistance");
        return distance;
    }

    public GHResponse setTime( long timeInMillis )
    {
        this.time = timeInMillis;
        return this;
    }

    /**
     * @return time in millis
     * @deprecated use getTime instead
     */
    public long getMillis()
    {
        check("getMillis");
        return time;
    }

    /**
     * @return time in millis
     */
    public long getTime()
    {
        check("getTimes");
        return time;
    }

    public GHResponse setRouteWeight( double weight )
    {
        this.routeWeight = weight;
        return this;
    }

    /**
     * This method returns a double value which is better than the time for comparison of routes but
     * only if you know what you are doing, e.g. only to compare routes gained with the same query
     * parameters like vehicle.
     */
    public double getRouteWeight()
    {
        check("getRouteWeight");
        return routeWeight;
    }

    /**
     * Calculates the bounding box of this route response
     */
    public BBox calcRouteBBox( BBox _fallback )
    {
        check("calcRouteBBox");
        BBox bounds = BBox.createInverse(_fallback.hasElevation());
        int len = list.getSize();
        if (len == 0)
            return _fallback;

        for (int i = 0; i < len; i++)
        {
            double lat = list.getLatitude(i);
            double lon = list.getLongitude(i);
            if (bounds.hasElevation())
            {
                double ele = list.getEle(i);
                bounds.update(lat, lon, ele);
            } else
            {
                bounds.update(lat, lon);
            }
        }
        return bounds;
    }

    @Override
    public String toString()
    {
        String str = "nodes:" + list.getSize() + ": " + list.toString();
        if (!instructions.isEmpty())
            str += ", " + instructions.toString();

        if (hasErrors())
            str += ", " + errors.toString();

        return str;
    }

    public void setInstructions( InstructionList instructions )
    {
        this.instructions = instructions;
    }

    public InstructionList getInstructions()
    {
        check("getInstructions");
        if (instructions == null)
            throw new IllegalArgumentException("To access instructions you need to enable creation before routing");

        return instructions;
    }
}
