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
package com.graphhopper.http;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.util.Downloader;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main wrapper of the offline API for a simple and efficient usage.
 * <p/>
 * @author Peter Karich
 */
public class GraphHopperWeb implements GraphHopperAPI
{
    public static void main( String[] args )
    {
        GraphHopperAPI gh = new GraphHopperWeb();
        gh.load("http://localhost:8989/api/route");
        //GHResponse ph = gh.route(new GHRequest(53.080827, 9.074707, 50.597186, 11.184082));
        GHResponse ph = gh.route(new GHRequest(49.6724, 11.3494, 49.6550, 11.4180));
        System.out.println(ph);
    }
    private Logger logger = LoggerFactory.getLogger(getClass());
    private String serviceUrl;
    private boolean encodePolyline = true;
    private Downloader downloader = new Downloader("GraphHopperWeb");

    public GraphHopperWeb()
    {
    }

    public void setDownloader( Downloader downloader )
    {
        this.downloader = downloader;
    }

    /**
     * Example url: http://localhost:8989/api or http://217.92.216.224:8080/api
     */
    @Override
    public boolean load( String url )
    {
        this.serviceUrl = url;
        return true;
    }

    public GraphHopperWeb setEncodePolyline( boolean b )
    {
        encodePolyline = b;
        return this;
    }

    @Override
    public GHResponse route( GHRequest request )
    {
        request.check();
        StopWatch sw = new StopWatch().start();
        double took = 0;
        try
        {
            String url = serviceUrl
                    + "?from=" + request.getFrom().lat + "," + request.getFrom().lon
                    + "&to=" + request.getTo().lat + "," + request.getTo().lon
                    + "&type=json"
                    + "&encodedPolyline=" + encodePolyline
                    + "&minPathPrecision=" + request.getHint("douglas.minprecision", 1)
                    + "&algo=" + request.getAlgorithm();
            String str = downloader.downloadAsString(url);
            JSONObject json = new JSONObject(str);
            took = json.getJSONObject("info").getDouble("took");
            JSONObject route = json.getJSONObject("route");
            double distance = route.getDouble("distance");
            int millis = route.getInt("time");
            PointList list;
            if (encodePolyline)
            {
                list = WebHelper.decodePolyline(route.getString("coordinates"), 100);
            } else
            {
                JSONArray coords = route.getJSONObject("data").getJSONArray("coordinates");
                list = new PointList(coords.length());
                for (int i = 0; i < coords.length(); i++)
                {
                    JSONArray arr = coords.getJSONArray(i);
                    double lon = arr.getDouble(0);
                    double lat = arr.getDouble(1);
                    list.add(lat, lon);
                }
            }
            return new GHResponse().setPoints(list).setDistance(distance).setMillis(millis);
        } catch (Exception ex)
        {
            throw new RuntimeException("Problem while fetching path " + request.getFrom() + "->" + request.getTo(), ex);
        } finally
        {
            logger.debug("Full request took:" + sw.stop().getSeconds() + ", API took:" + took);
        }
    }
}
