/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
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

import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class holds the data like points and instructions of a Path.
 * <p>
 * @author Peter Karich
 */
public class PathWrapper
{
    private List<String> description;
    private double distance;
    private double ascend;
    private double descend;
    private double routeWeight;
    private long time;
    private String debugInfo = "";
    private InstructionList instructions;
    private PointList waypointList = PointList.EMPTY;
    private PointList pointList = PointList.EMPTY;
    private final List<Throwable> errors = new ArrayList<Throwable>(4);

    /**
     * @return the description of this route alternative to make it meaningful for the user e.g. it
     * displays one or two main roads of the route.
     */
    public List<String> getDescription()
    {
        if (description == null)
            return Collections.emptyList();
        return description;
    }

    public PathWrapper setDescription( List<String> names )
    {
        this.description = names;
        return this;
    }

    public PathWrapper addDebugInfo( String debugInfo )
    {
        if (debugInfo == null)
            throw new IllegalStateException("Debug information has to be none null");

        if (!this.debugInfo.isEmpty())
            this.debugInfo += ";";

        this.debugInfo += debugInfo;
        return this;
    }

    public String getDebugInfo()
    {
        return debugInfo;
    }

    public PathWrapper setPoints( PointList points )
    {
        if (pointList != PointList.EMPTY)
            throw new IllegalStateException("Cannot call setPoint twice");

        pointList = points;
        return this;
    }

    /**
     * This method returns all points on the path. Keep in mind that calculating the distance from
     * these points might yield different results compared to getDistance as points could have been
     * simplified on import or after querying.
     */
    public PointList getPoints()
    {
        check("getPoints");
        return pointList;
    }

    /**
     * This method initializes this path with the snapped input points.
     */
    public void setWaypoints( PointList wpList )
    {
        if (waypointList != PointList.EMPTY)
            throw new IllegalStateException("Cannot call setWaypoints twice");

        this.waypointList = wpList;
    }

    /**
     * This method returns the input points snapped to the road network.
     */
    public PointList getWaypoints()
    {
        check("getWaypoints");
        return waypointList;
    }

    public PathWrapper setDistance( double distance )
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

    public PathWrapper setAscend( double ascend )
    {
        if (ascend < 0)
            throw new IllegalArgumentException("ascend has to be strictly positive");

        this.ascend = ascend;
        return this;
    }

    /**
     * This method returns the total elevation change (going upwards) in meter.
     * <p>
     * @return ascend in meter
     */
    public double getAscend()
    {
        return ascend;
    }

    public PathWrapper setDescend( double descend )
    {
        if (descend < 0)
            throw new IllegalArgumentException("descend has to be strictly positive");

        this.descend = descend;
        return this;
    }

    /**
     * This method returns the total elevation change (going downwards) in meter.
     * <p>
     * @return decline in meter
     */
    public double getDescend()
    {
        return descend;
    }

    public PathWrapper setTime( long timeInMillis )
    {
        this.time = timeInMillis;
        return this;
    }

    /**
     * @return time in millis
     */
    public long getTime()
    {
        check("getTimes");
        return time;
    }

    public PathWrapper setRouteWeight( double weight )
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
        int len = pointList.getSize();
        if (len == 0)
            return _fallback;

        for (int i = 0; i < len; i++)
        {
            double lat = pointList.getLatitude(i);
            double lon = pointList.getLongitude(i);
            if (bounds.hasElevation())
            {
                double ele = pointList.getEle(i);
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
        String str = "nodes:" + pointList.getSize() + "; " + pointList.toString();
        if (instructions != null && !instructions.isEmpty())
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

    private void check( String method )
    {
        if (hasErrors())
        {
            throw new RuntimeException("You cannot call " + method + " if response contains errors. Check this with ghResponse.hasErrors(). "
                    + "Errors are: " + getErrors());
        }
    }

    /**
     * @return true if this alternative response contains one or more errors
     */
    public boolean hasErrors()
    {
        return !errors.isEmpty();
    }

    public List<Throwable> getErrors()
    {
        return errors;
    }

    public PathWrapper addError( Throwable error )
    {
        errors.add(error);
        return this;
    }

    public PathWrapper addErrors( List<Throwable> errors )
    {
        this.errors.addAll(errors);
        return this;
    }
}
