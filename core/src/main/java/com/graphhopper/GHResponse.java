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

import com.graphhopper.util.PointList;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.shapes.BBox;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Wrapper to simplify output of GraphHopper.
 * <p/>
 * @author Peter Karich
 */
public class GHResponse
{
    private PointList list = PointList.EMPTY;
    private double distance;
    private long time;
    private String debugInfo = "";
    private List<Throwable> errors = new ArrayList<Throwable>(4);
    private InstructionList instructions = new InstructionList(0);

    public GHResponse()
    {
    }

    public GHResponse setPoints( PointList points )
    {
        list = points;
        return this;
    }

    public PointList getPoints()
    {
        return list;
    }

    public GHResponse setDistance( double distance )
    {
        this.distance = distance;
        return this;
    }

    /**
     * @return distance in meter
     */
    public double getDistance()
    {
        return distance;
    }

    public GHResponse setTime( long timeInSec )
    {
        this.time = timeInSec;
        return this;
    }

    /**
     * @return time in seconds
     */
    public long getTime()
    {
        return time;
    }

    public boolean isFound()
    {
        return list != null && !list.isEmpty();
    }

    public BBox calcRouteBBox( BBox _fallback )
    {
        BBox bounds = BBox.INVERSE.clone();
        int len = list.getSize();
        if (len == 0)
            return _fallback;

        for (int i = 0; i < len; i++)
        {
            double lat = list.getLatitude(i);
            double lon = list.getLongitude(i);
            if (lat > bounds.maxLat)
                bounds.maxLat = lat;

            if (lat < bounds.minLat)
                bounds.minLat = lat;

            if (lon > bounds.maxLon)
                bounds.maxLon = lon;

            if (lon < bounds.minLon)
                bounds.minLon = lon;
        }
        return bounds;
    }

    public String getDebugInfo()
    {
        return debugInfo;
    }

    public GHResponse setDebugInfo( String debugInfo )
    {
        this.debugInfo = debugInfo;
        return this;
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

    public GHResponse addError( Throwable error )
    {
        errors.add(error);
        return this;
    }

    @Override
    public String toString()
    {
        return "found:" + isFound() + ", nodes:" + list.getSize() + ": " + list.toString();
    }

    public void setInstructions( InstructionList instructions )
    {
        this.instructions = instructions;
    }

    public InstructionList getInstructions()
    {
        return instructions;
    }

    /**
     * Creates the GPX Format out of the points.
     * <p/>
     * TODO make it more reliable and use times from the route as well not only the points.
     * 
     * @return string to be stored as gpx file
     */
    public String createGPX( String trackName, long startTime )
    {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:SSZ");
        String header = "<?xml version='1.0' encoding='UTF-8' standalone='no' ?>"
                + "<gpx xmlns='http://www.topografix.com/GPX/1/1' >"
                + "<metadata>"
                + "<link href='http://graphhopper.com'>"
                + "<text>GraphHopper Maps</text>"
                + "</link>"
                + " <time>" + formatter.format(new Date()) + "</time>"
                + "</metadata>";
        StringBuilder track = new StringBuilder(header);
        track.append("<trk><name>").append(trackName).append("</name>");
        track.append("<trkseg>");
        PointList tmpList = getPoints();
        for (int i = 0; i < tmpList.getSize(); i++)
        {
            double lat = tmpList.getLatitude(i);
            double lon = tmpList.getLongitude(i);
            track.append("<trkpt lat='").append(lat).append("' lon='").append(lon).append("'>");

            // TODO add actual driving time
            startTime = startTime + 1;
            track.append("<time>").append(formatter.format(startTime)).append("</time>");

            track.append("</trkpt>");
        }
        track.append("</trkseg>");
        track.append("</trk></gpx>");
        return track.toString().replaceAll("\\'", "\"");
    }
}
