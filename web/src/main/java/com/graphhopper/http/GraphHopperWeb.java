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

import com.graphhopper.AltResponse;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import java.util.ArrayList;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main wrapper of the GraphHopper Directions API for a simple and efficient usage.
 * <p>
 * @author Peter Karich
 */
public class GraphHopperWeb implements GraphHopperAPI
{
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private Downloader downloader = new Downloader("GraphHopper Java Client");
    private String routeServiceUrl = "https://graphhopper.com/api/1/route";
    private String key = "";
    private boolean instructions = true;
    private boolean calcPoints = true;
    private boolean elevation = false;

    public GraphHopperWeb()
    {
    }

    public void setDownloader( Downloader downloader )
    {
        this.downloader = downloader;
    }

    @Override
    public boolean load( String serviceUrl )
    {
        this.routeServiceUrl = serviceUrl;
        return true;
    }

    public GraphHopperWeb setKey( String key )
    {
        if (key == null || key.isEmpty())
            throw new IllegalStateException("Key cannot be empty");

        this.key = key;
        return this;
    }

    public GraphHopperWeb setCalcPoints( boolean calcPoints )
    {
        this.calcPoints = calcPoints;
        return this;
    }

    public GraphHopperWeb setInstructions( boolean b )
    {
        instructions = b;
        return this;
    }

    public GraphHopperWeb setElevation( boolean withElevation )
    {
        this.elevation = withElevation;
        return this;
    }

    @Override
    public GHResponse route( GHRequest request )
    {
        try
        {
            String places = "";
            for (GHPoint p : request.getPoints())
            {
                places += "point=" + p.lat + "," + p.lon + "&";
            }

            boolean tmpInstructions = request.getHints().getBool("instructions", instructions);
            boolean tmpCalcPoints = request.getHints().getBool("calcPoints", calcPoints);

            if (tmpInstructions && !tmpCalcPoints)
                throw new IllegalStateException("Cannot calculate instructions without points (only points without instructions). "
                        + "Use calcPoints=false and instructions=false to disable point and instruction calculation");

            boolean tmpElevation = request.getHints().getBool("elevation", elevation);
            String tmpKey = request.getHints().get("key", key);

            String url = routeServiceUrl
                    + "?"
                    + places
                    + "&type=json"
                    + "&instructions=" + tmpInstructions
                    + "&points_encoded=true"
                    + "&calc_points=" + tmpCalcPoints
                    + "&algorithm=" + request.getAlgorithm()
                    + "&locale=" + request.getLocale().toString()
                    + "&elevation=" + tmpElevation;

            if (!request.getVehicle().isEmpty())
                url += "&vehicle=" + request.getVehicle();

            if (!tmpKey.isEmpty())
                url += "&key=" + tmpKey;
            int altMax = request.getHints().getInt("alternative_route.max_num", 0);
            if(altMax!=0){
            	url += "&alternative_route.max_num=" + altMax;            	
            }

            String str = downloader.downloadAsString(url, true);
            JSONObject json = new JSONObject(str);

            GHResponse res = new GHResponse();
            res.addErrors(readErrors(json));
            if (res.hasRawErrors())
                return res;

            JSONArray paths = json.getJSONArray("paths");
            for (int index = 0; index < paths.length(); index++)
            {
                JSONObject path = paths.getJSONObject(index);
                AltResponse altRsp = createAltResponse(path, tmpCalcPoints, tmpInstructions, tmpElevation);
                res.addAlternative(altRsp);
            }
            return res;

        } catch (Exception ex)
        {
            throw new RuntimeException("Problem while fetching path " + request.getPoints() + ": " + ex.getMessage(), ex);
        }
    }

    public static AltResponse createAltResponse( JSONObject path,
                                                 boolean tmpCalcPoints, boolean tmpInstructions, boolean tmpElevation )
    {
        AltResponse altRsp = new AltResponse();
        altRsp.addErrors(readErrors(path));
        if (altRsp.hasErrors())
            return altRsp;

        double distance = path.getDouble("distance");
        long time = path.getLong("time");
        if (tmpCalcPoints)
        {
            String pointStr = path.getString("points");
            PointList pointList = WebHelper.decodePolyline(pointStr, 100, tmpElevation);
            altRsp.setPoints(pointList);

            if (tmpInstructions)
            {
                JSONArray instrArr = path.getJSONArray("instructions");

                InstructionList il = new InstructionList(null);
                int viaCount = 1;
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
                    PointList instPL = new PointList(to - from, tmpElevation);
                    for (int j = from; j <= to; j++)
                    {
                        instPL.add(pointList, j);
                    }

                    InstructionAnnotation ia = InstructionAnnotation.EMPTY;
                    if (jsonObj.has("annotation_importance") && jsonObj.has("annotation_text"))
                    {
                        ia = new InstructionAnnotation(jsonObj.getInt("annotation_importance"), jsonObj.getString("annotation_text"));
                    }

                    Instruction instr;
                    if (sign == Instruction.USE_ROUNDABOUT || sign == Instruction.LEAVE_ROUNDABOUT)
                    {
                        instr = new RoundaboutInstruction(sign, text, ia, instPL);
                    } else if (sign == Instruction.REACHED_VIA)
                    {
                        ViaInstruction tmpInstr = new ViaInstruction(text, ia, instPL);
                        tmpInstr.setViaCount(viaCount);
                        viaCount++;
                        instr = tmpInstr;
                    } else if (sign == Instruction.FINISH)
                    {
                        instr = new FinishInstruction(instPL, 0);
                    } else
                    {
                        instr = new Instruction(sign, text, ia, instPL);
                    }

                    // The translation is done from the routing service so just use the provided string
                    // instead of creating a combination with sign and name etc
                    instr.setUseRawName();

                    instr.setDistance(instDist).setTime(instTime);
                    il.add(instr);
                }
                altRsp.setInstructions(il);
            }
        }
        altRsp.setDistance(distance).setTime(time);
        return altRsp;
    }

    public static List<Throwable> readErrors( JSONObject json )
    {
        List<Throwable> errors = new ArrayList<Throwable>();
        JSONArray errorJson;

        if (json.has("message"))
        {
            if (json.has("hints"))
            {
                errorJson = json.getJSONArray("hints");
            } else
            {
                // should not happen
                errors.add(new RuntimeException(json.getString("message")));
                return errors;
            }
        } else if (json.has("info"))
        {
            // deprecated JSON format for errors, remove in 0.5 release
            JSONObject jsonInfo = json.getJSONObject("info");
            if (jsonInfo.has("errors"))
                errorJson = jsonInfo.getJSONArray("errors");
            else
                return errors;

        } else
            return errors;

        for (int i = 0; i < errorJson.length(); i++)
        {
            JSONObject error = errorJson.getJSONObject(i);
            String exClass = "";
            if (error.has("details"))
                exClass = error.getString("details");

            String exMessage = error.getString("message");

            if (exClass.equals(UnsupportedOperationException.class.getName()))
                errors.add(new UnsupportedOperationException(exMessage));
            else if (exClass.equals(IllegalStateException.class.getName()))
                errors.add(new IllegalStateException(exMessage));
            else if (exClass.equals(RuntimeException.class.getName()))
                errors.add(new RuntimeException(exMessage));
            else if (exClass.equals(IllegalArgumentException.class.getName()))
                errors.add(new IllegalArgumentException(exMessage));
            else if (exClass.isEmpty())
                errors.add(new RuntimeException(exMessage));
            else
                errors.add(new RuntimeException(exClass + " " + exMessage));
        }

        if (json.has("message") && errors.isEmpty())
            errors.add(new RuntimeException(json.getString("message")));

        return errors;
    }
}
