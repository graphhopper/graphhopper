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
package com.graphhopper.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.PathWrapper;
import com.graphhopper.http.WebHelper;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.util.*;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.exceptions.*;
import com.graphhopper.util.shapes.GHPoint;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.graphhopper.util.Helper.round6;
import static com.graphhopper.util.Helper.toLowerCase;

/**
 * Main wrapper of the GraphHopper Directions API for a simple and efficient
 * usage.
 *
 * @author Peter Karich
 */
public class GraphHopperWeb implements GraphHopperAPI {

    private final ObjectMapper objectMapper;
    private OkHttpClient downloader;
    private String routeServiceUrl;
    private String key = "";
    private boolean instructions = true;
    private boolean calcPoints = true;
    private boolean elevation = false;
    private String optimize = "false";
    private final Set<String> ignoreSet;

    public static final String TIMEOUT = "timeout";
    private final long DEFAULT_TIMEOUT = 5000;

    public GraphHopperWeb() {
        this("https://graphhopper.com/api/1/route");
    }

    public GraphHopperWeb(String serviceUrl) {
        this.routeServiceUrl = serviceUrl;
        downloader = new OkHttpClient.Builder().
                connectTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS).
                readTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS).
                build();

        // some parameters are supported directly via Java API so ignore them when writing the getHints map
        ignoreSet = new HashSet<>();
        ignoreSet.add("calc_points");
        ignoreSet.add("calcpoints");
        ignoreSet.add("instructions");
        ignoreSet.add("elevation");
        ignoreSet.add("key");
        ignoreSet.add("optimize");

        // some parameters are in the request:
        ignoreSet.add("algorithm");
        ignoreSet.add("locale");
        ignoreSet.add("point");
        ignoreSet.add("vehicle");

        // some are special and need to be avoided
        ignoreSet.add("points_encoded");
        ignoreSet.add("pointsencoded");
        ignoreSet.add("type");
        objectMapper = Jackson.newObjectMapper();
    }

    public GraphHopperWeb setDownloader(OkHttpClient downloader) {
        this.downloader = downloader;
        return this;
    }

    public OkHttpClient getDownloader() {
        return downloader;
    }

    PathWrapper createPathWrapper(JsonNode path, boolean tmpElevation, boolean turnDescription) {
        PathWrapper pathWrapper = new PathWrapper();
        pathWrapper.addErrors(readErrors(path));
        if (pathWrapper.hasErrors())
            return pathWrapper;

        if (path.has("snapped_waypoints")) {
            String snappedPointStr = path.get("snapped_waypoints").asText();
            PointList snappedPoints = WebHelper.decodePolyline(snappedPointStr, 5, tmpElevation);
            pathWrapper.setWaypoints(snappedPoints);
        }

        if (path.has("ascend")) {
            pathWrapper.setAscend(path.get("ascend").asDouble());
        }
        if (path.has("descend")) {
            pathWrapper.setDescend(path.get("descend").asDouble());
        }
        if (path.has("weight")) {
            pathWrapper.setRouteWeight(path.get("weight").asDouble());
        }
        if (path.has("description")) {
            JsonNode descriptionNode = path.get("description");
            if (descriptionNode.isArray()) {
                List<String> description = new ArrayList<>(descriptionNode.size());
                for (JsonNode descNode : descriptionNode) {
                    description.add(descNode.asText());
                }
                pathWrapper.setDescription(description);
            } else {
                throw new IllegalStateException("Description has to be an array");
            }
        }

        if (path.has("points")) {
            String pointStr = path.get("points").asText();
            PointList pointList = WebHelper.decodePolyline(pointStr, 100, tmpElevation);
            pathWrapper.setPoints(pointList);

            if (path.has("instructions")) {
                JsonNode instrArr = path.get("instructions");

                InstructionList il = new InstructionList(null);
                int viaCount = 1;
                for (JsonNode jsonObj : instrArr) {
                    double instDist = jsonObj.get("distance").asDouble();
                    String text = turnDescription ? jsonObj.get("text").asText() : jsonObj.get("street_name").asText();
                    long instTime = jsonObj.get("time").asLong();
                    int sign = jsonObj.get("sign").asInt();
                    JsonNode iv = jsonObj.get("interval");
                    int from = iv.get(0).asInt();
                    int to = iv.get(1).asInt();
                    PointList instPL = new PointList(to - from, tmpElevation);
                    for (int j = from; j <= to; j++) {
                        instPL.add(pointList, j);
                    }

                    InstructionAnnotation ia = InstructionAnnotation.EMPTY;
                    if (jsonObj.has("annotation_importance") && jsonObj.has("annotation_text")) {
                        ia = new InstructionAnnotation(jsonObj.get("annotation_importance").asInt(), jsonObj.get("annotation_text").asText());
                    }

                    Instruction instr;
                    if (sign == Instruction.USE_ROUNDABOUT || sign == Instruction.LEAVE_ROUNDABOUT) {
                        RoundaboutInstruction ri = new RoundaboutInstruction(sign, text, ia, instPL);

                        if (jsonObj.has("exit_number")) {
                            ri.setExitNumber(jsonObj.get("exit_number").asInt());
                        }

                        if (jsonObj.has("exited")) {
                            if (jsonObj.get("exited").asBoolean())
                                ri.setExited();
                        }

                        if (jsonObj.has("turn_angle")) {
                            // TODO provide setTurnAngle setter
                            double angle = jsonObj.get("turn_angle").asDouble();
                            ri.setDirOfRotation(angle);
                            ri.setRadian((angle < 0 ? -Math.PI : Math.PI) - angle);
                        }

                        instr = ri;
                    } else if (sign == Instruction.REACHED_VIA) {
                        ViaInstruction tmpInstr = new ViaInstruction(text, ia, instPL);
                        tmpInstr.setViaCount(viaCount);
                        viaCount++;
                        instr = tmpInstr;
                    } else if (sign == Instruction.FINISH) {
                        instr = new FinishInstruction(text, instPL, 0);
                    } else {
                        instr = new Instruction(sign, text, ia, instPL);
                        if (sign == Instruction.CONTINUE_ON_STREET) {
                            if (jsonObj.has("heading")) {
                                instr.setExtraInfo("heading", jsonObj.get("heading").asDouble());
                            }
                        }
                    }

                    // Usually, the translation is done from the routing service so just use the provided string
                    // instead of creating a combination with sign and name etc.
                    // This is called the turn description.
                    // This can be changed by passing <code>turn_description=false</code>.
                    if (turnDescription)
                        instr.setUseRawName();

                    instr.setDistance(instDist).setTime(instTime);
                    il.add(instr);
                }
                pathWrapper.setInstructions(il);
            }

            if (path.has("details")) {
                JsonNode details = path.get("details");
                Map<String, List<PathDetail>> pathDetails = new HashMap<>(details.size());
                Iterator<Map.Entry<String, JsonNode>> detailIterator = details.fields();
                while (detailIterator.hasNext()) {
                    Map.Entry<String, JsonNode> detailEntry = detailIterator.next();
                    List<PathDetail> pathDetailList = new ArrayList<>();
                    for (JsonNode pathDetail : detailEntry.getValue()) {
                        PathDetail pd = objectMapper.convertValue(pathDetail, PathDetail.class);
                        pathDetailList.add(pd);
                    }
                    pathDetails.put(detailEntry.getKey(), pathDetailList);
                }
                pathWrapper.addPathDetails(pathDetails);
            }
        }

        double distance = path.get("distance").asDouble();
        long time = path.get("time").asLong();
        pathWrapper.setDistance(distance).setTime(time);
        return pathWrapper;
    }

    // Credits to: http://stackoverflow.com/a/24012023/194609
    private Map<String, Object> toMap(JsonNode object) {
        return objectMapper.convertValue(object, new TypeReference<Map<String, Object>>() {
        });
    }

    public List<Throwable> readErrors(JsonNode json) {
        List<Throwable> errors = new ArrayList<>();
        JsonNode errorJson;

        if (json.has("message")) {
            if (json.has("hints")) {
                errorJson = json.get("hints");
            } else {
                // should not happen
                errors.add(new RuntimeException(json.get("message").asText()));
                return errors;
            }
        } else
            return errors;

        for (JsonNode error : errorJson) {
            String exClass = "";
            if (error.has("details"))
                exClass = error.get("details").asText();

            String exMessage = error.get("message").asText();

            if (exClass.equals(UnsupportedOperationException.class.getName()))
                errors.add(new UnsupportedOperationException(exMessage));
            else if (exClass.equals(IllegalStateException.class.getName()))
                errors.add(new IllegalStateException(exMessage));
            else if (exClass.equals(RuntimeException.class.getName()))
                errors.add(new DetailedRuntimeException(exMessage, toMap(error)));
            else if (exClass.equals(IllegalArgumentException.class.getName()))
                errors.add(new DetailedIllegalArgumentException(exMessage, toMap(error)));
            else if (exClass.equals(ConnectionNotFoundException.class.getName())) {
                errors.add(new ConnectionNotFoundException(exMessage, toMap(error)));
            } else if (exClass.equals(PointNotFoundException.class.getName())) {
                int pointIndex = error.get("point_index").asInt();
                errors.add(new PointNotFoundException(exMessage, pointIndex));
            } else if (exClass.equals(PointOutOfBoundsException.class.getName())) {
                int pointIndex = error.get("point_index").asInt();
                errors.add(new PointOutOfBoundsException(exMessage, pointIndex));
            } else if (exClass.isEmpty())
                errors.add(new DetailedRuntimeException(exMessage, toMap(error)));
            else
                errors.add(new DetailedRuntimeException(exClass + " " + exMessage, toMap(error)));
        }

        if (json.has("message") && errors.isEmpty())
            errors.add(new RuntimeException(json.get("message").asText()));

        return errors;
    }

    @Override
    public boolean load(String serviceUrl) {
        this.routeServiceUrl = serviceUrl;
        return true;
    }

    public GraphHopperWeb setKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException("Key cannot be empty");
        }

        this.key = key;
        return this;
    }

    /**
     * Enable or disable calculating points for the way. The default is true.
     */
    public GraphHopperWeb setCalcPoints(boolean calcPoints) {
        this.calcPoints = calcPoints;
        return this;
    }

    /**
     * Enable or disable calculating and returning turn instructions. The
     * default is true.
     */
    public GraphHopperWeb setInstructions(boolean b) {
        instructions = b;
        return this;
    }

    /**
     * Enable or disable elevation. The default is false.
     */
    public GraphHopperWeb setElevation(boolean withElevation) {
        this.elevation = withElevation;
        return this;
    }

    /**
     * @param optimize "false" if the order of the locations should be left
     *                 unchanged, this is the default. Or if "true" then the order of the
     *                 location is optimized according to the overall best route and returned
     *                 this way i.e. the traveling salesman problem is solved under the hood.
     *                 Note that in this case the request takes longer and costs more credits.
     *                 For more details see:
     *                 https://github.com/graphhopper/directions-api/blob/master/FAQ.md#what-is-one-credit
     */
    public GraphHopperWeb setOptimize(String optimize) {
        this.optimize = optimize;
        return this;
    }


    @Override
    public GHResponse route(GHRequest request) {
        ResponseBody rspBody = null;
        try {
            Request okRequest = createRequest(request);
            rspBody = getClientForRequest(request).newCall(okRequest).execute().body();
            JsonNode json = objectMapper.reader().readTree(rspBody.byteStream());

            GHResponse res = new GHResponse();
            res.addErrors(readErrors(json));
            if (res.hasErrors())
                return res;

            JsonNode paths = json.get("paths");

            boolean tmpElevation = request.getHints().getBool("elevation", elevation);
            boolean tmpTurnDescription = request.getHints().getBool("turn_description", true);

            for (JsonNode path : paths) {
                PathWrapper altRsp = createPathWrapper(path, tmpElevation, tmpTurnDescription);
                res.add(altRsp);
            }

            return res;

        } catch (Exception ex) {
            throw new RuntimeException("Problem while fetching path " + request.getPoints() + ": " + ex.getMessage(), ex);
        } finally {
            Helper.close(rspBody);
        }
    }

    private OkHttpClient getClientForRequest(GHRequest request) {
        OkHttpClient client = this.downloader;
        if (request.getHints().has(TIMEOUT)) {
            long timeout = request.getHints().getLong(TIMEOUT, DEFAULT_TIMEOUT);
            client = client.newBuilder()
                    .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                    .readTimeout(timeout, TimeUnit.MILLISECONDS)
                    .build();
        }

        return client;
    }

    private Request createRequest(GHRequest request) {
        boolean tmpInstructions = request.getHints().getBool("instructions", instructions);
        boolean tmpCalcPoints = request.getHints().getBool("calc_points", calcPoints);
        String tmpOptimize = request.getHints().get("optimize", optimize);

        if (tmpInstructions && !tmpCalcPoints) {
            throw new IllegalStateException("Cannot calculate instructions without points (only points without instructions). "
                    + "Use calc_points=false and instructions=false to disable point and instruction calculation");
        }

        boolean tmpElevation = request.getHints().getBool("elevation", elevation);

        String places = "";
        for (GHPoint p : request.getPoints()) {
            places += "point=" + round6(p.lat) + "," + round6(p.lon) + "&";
        }

        String type = request.getHints().get("type", "json");

        String url = routeServiceUrl
                + "?"
                + places
                + "&type=" + type
                + "&instructions=" + tmpInstructions
                + "&points_encoded=true"
                + "&calc_points=" + tmpCalcPoints
                + "&algorithm=" + request.getAlgorithm()
                + "&locale=" + request.getLocale().toString()
                + "&elevation=" + tmpElevation
                + "&optimize=" + tmpOptimize;

        if (!request.getVehicle().isEmpty()) {
            url += "&vehicle=" + request.getVehicle();
        }

        for (String details : request.getPathDetails()) {
            url += "&" + Parameters.DETAILS.PATH_DETAILS + "=" + details;
        }

        for (String hint : request.getPointHints()) {
            url += "&point_hint=" + WebHelper.encodeURL(hint);
        }

        if (!key.isEmpty()) {
            url += "&key=" + WebHelper.encodeURL(key);
        }

        for (Map.Entry<String, String> entry : request.getHints().toMap().entrySet()) {
            String urlKey = entry.getKey();
            String urlValue = entry.getValue();

            // use lower case conversion for check only!
            if (ignoreSet.contains(toLowerCase(urlKey))) {
                continue;
            }

            if (urlValue != null && !urlValue.isEmpty()) {
                url += "&" + WebHelper.encodeURL(urlKey) + "=" + WebHelper.encodeURL(urlValue);
            }
        }

        return new Request.Builder().url(url).build();
    }

    public String export(GHRequest ghRequest) {
        String str = "Creating request failed";
        try {
            Request okRequest = createRequest(ghRequest);
            str = getClientForRequest(ghRequest).newCall(okRequest).execute().body().string();

            return str;
        } catch (Exception ex) {
            throw new RuntimeException("Problem while fetching export " + ghRequest.getPoints()
                    + ", error: " + ex.getMessage() + " response: " + str, ex);
        }
    }
}
