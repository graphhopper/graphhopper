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

import com.graphhopper.search.Geocoding;
import com.graphhopper.search.ReverseGeocoding;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPlace;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Online request for (reverse) geocoding.
 * <p/>
 * @author Peter Karich
 */
public class NominatimGeocoder implements Geocoding, ReverseGeocoding
{
    public static void main( String[] args )
    {
        System.out.println("search " + new NominatimGeocoder().names2places(new GHPlace("bayreuth"), new GHPlace("berlin")));

        System.out.println("reverse " + new NominatimGeocoder().places2names(new GHPlace(49.9027606, 11.577197),
                new GHPlace(52.5198535, 13.4385964)));
    }

    private String nominatimUrl;
    private String nominatimReverseUrl;
    private BBox bounds;
    private Logger logger = LoggerFactory.getLogger(getClass());
    private int timeoutInMillis = 10000;
    private String userAgent = "GraphHopper Web Service";

    public NominatimGeocoder()
    {
        this("http://open.mapquestapi.com/nominatim/v1/search.php",
                "http://open.mapquestapi.com/nominatim/v1/reverse.php");
    }

    public NominatimGeocoder( String url, String reverseUrl )
    {
        this.nominatimUrl = url;
        this.nominatimReverseUrl = reverseUrl;
    }

    public NominatimGeocoder setBounds( BBox bounds )
    {
        this.bounds = bounds;
        return this;
    }

    @Override
    public List<GHPlace> names2places( GHPlace... places )
    {
        List<GHPlace> resList = new ArrayList<GHPlace>();
        for (GHPlace place : places)
        {
            // see https://trac.openstreetmap.org/ticket/4683 why limit=3 and not 1
            String url = nominatimUrl + "?format=json&q=" + WebHelper.encodeURL(place.getName()) + "&limit=3";
            if (bounds != null)
            {
                // minLon, minLat, maxLon, maxLat => left, top, right, bottom
                url += "&bounded=1&viewbox=" + bounds.minLon + "," + bounds.maxLat + "," + bounds.maxLon + "," + bounds.minLat;
            }

            try
            {
                HttpURLConnection hConn = openConnection(url);
                String str = WebHelper.readString(hConn.getInputStream());
                // System.out.println(str);
                // TODO sort returned objects by bounding box area size?
                JSONObject json = new JSONArray(str).getJSONObject(0);
                double lat = json.getDouble("lat");
                double lon = json.getDouble("lon");
                GHPlace p = new GHPlace(lat, lon);
                p.setName(json.getString("display_name"));
                resList.add(p);
            } catch (Exception ex)
            {
                logger.error("problem while geocoding (search " + place + "): " + ex.getMessage());
            }
        }
        return resList;
    }

    @Override
    public List<GHPlace> places2names( GHPlace... points )
    {
        List<GHPlace> resList = new ArrayList<GHPlace>();
        for (GHPlace point : points)
        {
            try
            {
                String url = nominatimReverseUrl + "?lat=" + point.lat + "&lon=" + point.lon
                        + "&format=json&zoom=16";
                HttpURLConnection hConn = openConnection(url);
                String str = WebHelper.readString(hConn.getInputStream());
                // System.out.println(str);
                JSONObject json = new JSONObject(str);
                double lat = json.getDouble("lat");
                double lon = json.getDouble("lon");

                JSONObject address = json.getJSONObject("address");
                String name = "";
                if (address.has("road"))
                {
                    name += address.get("road") + ", ";
                }
                if (address.has("postcode"))
                {
                    name += address.get("postcode") + " ";
                }
                if (address.has("city"))
                {
                    name += address.get("city") + ", ";
                } else if (address.has("county"))
                {
                    name += address.get("county") + ", ";
                }
                if (address.has("state"))
                {
                    name += address.get("state") + ", ";
                }
                if (address.has("country"))
                {
                    name += address.get("country");
                }
                resList.add(new GHPlace(lat, lon).setName(name));
            } catch (Exception ex)
            {
                logger.error("problem while geocoding (reverse " + point + "): " + ex.getMessage());
            }
        }
        return resList;
    }

    HttpURLConnection openConnection( String url ) throws IOException
    {
        HttpURLConnection hConn = (HttpURLConnection) new URL(url).openConnection();
        ;
        hConn.setRequestProperty("User-Agent", userAgent);
        hConn.setRequestProperty("content-charset", "UTF-8");
        hConn.setConnectTimeout(timeoutInMillis);
        hConn.setReadTimeout(timeoutInMillis);
        hConn.connect();
        return hConn;
    }

    public NominatimGeocoder setTimeout( int timeout )
    {
        this.timeoutInMillis = timeout;
        return this;
    }
}
