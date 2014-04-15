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
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.util.Downloader;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPlace;
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
        gh.load("http://localhost:8989/route");
        //GHResponse ph = gh.route(new GHRequest(53.080827, 9.074707, 50.597186, 11.184082));
        GHResponse ph = gh.route(new GHRequest(49.6724, 11.3494, 49.6550, 11.4180));
        System.out.println(ph);
    }
    private Logger logger = LoggerFactory.getLogger(getClass());
    private String serviceUrl;
    private boolean pointsEncoded = true;
    private Downloader downloader = new Downloader("GraphHopperWeb");
    private boolean instructions = true;

    public GraphHopperWeb()
    {
    }

    public void setDownloader( Downloader downloader )
    {
        this.downloader = downloader;
    }

    /**
     * Example url: http://localhost:8989 or http://217.92.216.224:8080
     */
    @Override
    public boolean load( String url )
    {
        this.serviceUrl = url;
        return true;
    }

    public GraphHopperWeb setPointsEncoded( boolean b )
    {
        pointsEncoded = b;
        return this;
    }

    public GraphHopperWeb setInstructions( boolean b )
    {
        instructions = b;
        return this;
    }
    
    @Override
    public GHResponse route( GHRequest request )
    {
        StopWatch sw = new StopWatch().start();
        double took = 0;
        try
        {
            String places = "";
            for (GHPlace p : request.getPlaces())
            {
                places += "point=" + p.lat + "," + p.lon + "&";
            }
            String url = serviceUrl
                    + "?"
                    + places
                    + "&type=json"
                    + "&points_encoded=" + pointsEncoded
                    + "&min_path_precision=" + request.getHint("douglas.minprecision", 1)
                    + "&algo=" + request.getAlgorithm();
            String str = downloader.downloadAsString(url);
            JSONObject json = new JSONObject(str);
            took = json.getJSONObject("info").getDouble("took");
            JSONArray paths = json.getJSONArray("paths");
            JSONObject firstPath = paths.getJSONObject(0);

            boolean is3D = false;
            if (firstPath.has("points_dim"))
                is3D = "3".equals(firstPath.getString("points_dim"));
            double distance = firstPath.getDouble("distance");
            int time = firstPath.getInt("time");
            PointList pointList;
            if (pointsEncoded)
            {
                pointList = WebHelper.decodePolyline(firstPath.getString("points"), 100, is3D);
            } else
            {
                JSONArray coords = firstPath.getJSONObject("points").getJSONArray("coordinates");
                pointList = new PointList(coords.length(), is3D);
                for (int i = 0; i < coords.length(); i++)
                {
                    JSONArray arr = coords.getJSONArray(i);
                    double lon = arr.getDouble(0);
                    double lat = arr.getDouble(1);
                    if (is3D)
                        pointList.add(lat, lon, arr.getDouble(2));
                    else
                        pointList.add(lat, lon);
                }
            }
            GHResponse res = new GHResponse();
            if (instructions)
            {
                JSONArray instrArr = firstPath.getJSONArray("instructions");

                InstructionList il = new InstructionList();
                for (int instrIndex = 0; instrIndex < instrArr.length(); instrIndex++)
                {
                    JSONObject jsonObj = instrArr.getJSONObject(instrIndex);
                    double instDist = jsonObj.getDouble("distance");
                    String text = jsonObj.getString("text");
                    long instTime = jsonObj.getLong("time");
                    int sign = jsonObj.getInt("sign");
                    JSONArray iv = jsonObj.getJSONArray("interval");
                    int from = iv.getInt(0);
                    int to = iv.getInt(1);
                    PointList instPL = new PointList(to - from, is3D);
                    for (int j = from; j <= to; j++)
                    {
                        instPL.add(pointList, j);
                    }

                    // TODO way and payment type
                    Instruction instr = new Instruction(sign, text, -1, -1, instPL).
                            setDistance(instDist).setTime(instTime);
                    il.add(instr);
                }
                res.setInstructions(il);
            }
            return res.setPoints(pointList).setDistance(distance).setMillis(time);
        } catch (Exception ex)
        {
            throw new RuntimeException("Problem while fetching path " + request.getPlaces(), ex);
        } finally
        {
            logger.debug("Full request took:" + sw.stop().getSeconds() + ", API took:" + took);
        }
    }
}
