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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.jackson.ResponsePathDeserializer;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.graphhopper.api.GraphHopperMatrixWeb.*;
import static com.graphhopper.api.Version.GH_VERSION_FROM_MAVEN;
import static com.graphhopper.util.Helper.round6;
import static com.graphhopper.util.Helper.toLowerCase;
import static com.graphhopper.util.Parameters.Routing.CALC_POINTS;
import static com.graphhopper.util.Parameters.Routing.INSTRUCTIONS;

/**
 * Main wrapper of the GraphHopper Directions API for a simple and efficient
 * usage.
 *
 * @author Peter Karich
 */
public class GraphHopperWeb {

    public static final String X_GH_CLIENT_VERSION = "X-GH-Client-Version";
    private final ObjectMapper objectMapper;
    private final String routeServiceUrl;
    private OkHttpClient downloader;
    private String key = "";
    private boolean instructions = true;
    private boolean calcPoints = true;
    private boolean elevation = false;
    private String optimize = "false";
    private boolean postRequest = true;
    private int maxUnzippedLength = 1000;
    private final Set<String> ignoreSetForGet;
    private final Set<String> ignoreSetForPost;

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
                addInterceptor(new GzipRequestInterceptor()).
                build();
        // some parameters are supported directly via Java API so ignore them when writing the getHints map
        ignoreSetForPost = new HashSet<>();
        ignoreSetForPost.add(KEY);
        ignoreSetForPost.add(SERVICE_URL);
        ignoreSetForPost.add(CALC_POINTS);
        ignoreSetForPost.add(INSTRUCTIONS);
        ignoreSetForPost.add("elevation");
        ignoreSetForPost.add("optimize");
        ignoreSetForPost.add("points_encoded");
        ignoreSetForPost.add("points_encoded_multiplier");

        ignoreSetForGet = new HashSet<>();
        ignoreSetForGet.add(KEY);
        ignoreSetForGet.add(CALC_POINTS);
        ignoreSetForGet.add("calcpoints");
        ignoreSetForGet.add(INSTRUCTIONS);
        ignoreSetForGet.add("elevation");
        ignoreSetForGet.add("optimize");

        // some parameters are in the request:
        ignoreSetForGet.add("algorithm");
        ignoreSetForGet.add("locale");
        ignoreSetForGet.add("point");

        // some are special and need to be avoided
        ignoreSetForGet.add("points_encoded");
        ignoreSetForGet.add("pointsencoded");
        ignoreSetForGet.add("points_encoded_multiplier");
        ignoreSetForGet.add("type");
        objectMapper = Jackson.newObjectMapper();
    }

    public GraphHopperWeb setMaxUnzippedLength(int maxUnzippedLength) {
        this.maxUnzippedLength = maxUnzippedLength;
        return this;
    }

    public GraphHopperWeb setDownloader(OkHttpClient downloader) {
        this.downloader = downloader;
        return this;
    }

    public OkHttpClient getDownloader() {
        return downloader;
    }

    public GraphHopperWeb setKey(String key) {
        Objects.requireNonNull(key, "Key must not be null");
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Key must not be empty");
        }

        this.key = key;
        return this;
    }


    /**
     * Use new endpoint 'POST /route' instead of 'GET /route'
     */
    public GraphHopperWeb setPostRequest(boolean postRequest) {
        this.postRequest = postRequest;
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

    public GHResponse route(GHRequest ghRequest) {
        ResponseBody rspBody = null;
        try {
            boolean tmpElevation = ghRequest.getHints().getBool("elevation", elevation);
            boolean tmpTurnDescription = ghRequest.getHints().getBool("turn_description", true);
            ghRequest.getHints().remove("turn_description"); // do not include in request

            Request okRequest = postRequest ? createPostRequest(ghRequest) : createGetRequest(ghRequest);
            rspBody = getClientForRequest(ghRequest).newCall(okRequest).execute().body();
            JsonNode json = objectMapper.reader().readTree(rspBody.byteStream());

            GHResponse res = new GHResponse();
            res.addErrors(ResponsePathDeserializer.readErrors(objectMapper, json));
            if (res.hasErrors())
                return res;

            JsonNode paths = json.get("paths");
            for (JsonNode path : paths) {
                ResponsePath altRsp = ResponsePathDeserializer.createResponsePath(objectMapper, path, tmpElevation, 1e6, tmpTurnDescription);
                res.add(altRsp);
            }

            JsonNode b = json.get("hints");
            PMap hints = new PMap();
            b.fields().forEachRemaining(f -> hints.putObject(f.getKey(), Helper.toObject(f.getValue().asText())));
            res.setHints(hints);

            return res;

        } catch (Exception ex) {
            throw new RuntimeException("Problem while fetching path " + ghRequest.getPoints() + ": " + ex.getMessage(), ex);
        } finally {
            Helper.close(rspBody);
        }
    }

    OkHttpClient getClientForRequest(GHRequest request) {
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

    Request createPostRequest(GHRequest ghRequest) {
        String tmpServiceURL = ghRequest.getHints().getString(SERVICE_URL, routeServiceUrl);
        String url = tmpServiceURL + "?";
        if (!Helper.isEmpty(key))
            url += "key=" + key;

        ObjectNode requestJson = requestToJson(ghRequest);
        String body;
        try {
            body = objectMapper.writeValueAsString(requestJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not write request body", e);
        }
        Request.Builder builder = new Request.Builder().url(url).post(RequestBody.create(MT_JSON, body));
        builder.header(X_GH_CLIENT_VERSION, GH_VERSION_FROM_MAVEN);
        // force avoiding our GzipRequestInterceptor for smaller requests ~30 locations
        if (body.length() < maxUnzippedLength)
            builder.header("Content-Encoding", "identity");
        return builder.build();
    }

    ObjectNode requestToJson(GHRequest ghRequest) {
        ObjectNode requestJson = objectMapper.createObjectNode();
        requestJson.putArray("points").addAll(createPointList(ghRequest.getPoints()));
        if (!ghRequest.getPointHints().isEmpty())
            requestJson.putArray("point_hints").addAll(createStringList(ghRequest.getPointHints()));
        if (!ghRequest.getHeadings().isEmpty())
            requestJson.putArray("headings").addAll(createDoubleList(ghRequest.getHeadings()));
        if (!ghRequest.getCurbsides().isEmpty())
            requestJson.putArray("curbsides").addAll(createStringList(ghRequest.getCurbsides()));
        if (!ghRequest.getSnapPreventions().isEmpty())
            requestJson.putArray("snap_preventions").addAll(createStringList(ghRequest.getSnapPreventions()));
        if (!ghRequest.getPathDetails().isEmpty())
            requestJson.putArray("details").addAll(createStringList(ghRequest.getPathDetails()));

        requestJson.put("locale", ghRequest.getLocale().toString());
        if (!ghRequest.getProfile().isEmpty())
            requestJson.put("profile", ghRequest.getProfile());
        if (!ghRequest.getAlgorithm().isEmpty())
            requestJson.put("algorithm", ghRequest.getAlgorithm());

        requestJson.put("points_encoded", true);
        requestJson.put("points_encoded_multiplier", 1e6);
        requestJson.put(INSTRUCTIONS, ghRequest.getHints().getBool(INSTRUCTIONS, instructions));
        requestJson.put(CALC_POINTS, ghRequest.getHints().getBool(CALC_POINTS, calcPoints));
        requestJson.put("elevation", ghRequest.getHints().getBool("elevation", elevation));
        requestJson.put("optimize", ghRequest.getHints().getString("optimize", optimize));
        if (ghRequest.getCustomModel() != null)
            requestJson.putPOJO(CustomModel.KEY, ghRequest.getCustomModel());

        Map<String, Object> hintsMap = ghRequest.getHints().toMap();
        for (Map.Entry<String, Object> entry : hintsMap.entrySet()) {
            String hintKey = entry.getKey();
            if (ignoreSetForPost.contains(hintKey))
                continue;

            // special case for String required, see testPutPOJO
            if (entry.getValue() instanceof String)
                requestJson.put(hintKey, (String) entry.getValue());
            else
                requestJson.putPOJO(hintKey, entry.getValue());
        }
        return requestJson;
    }

    Request createGetRequest(GHRequest ghRequest) {
        if (ghRequest.getCustomModel() != null)
            throw new IllegalArgumentException("Custom models cannot be used for GET requests. Use setPostRequest(true)");

        boolean tmpInstructions = ghRequest.getHints().getBool(INSTRUCTIONS, instructions);
        boolean tmpCalcPoints = ghRequest.getHints().getBool(CALC_POINTS, calcPoints);
        String tmpOptimize = ghRequest.getHints().getString("optimize", optimize);

        if (tmpInstructions && !tmpCalcPoints) {
            throw new IllegalStateException("Cannot calculate instructions without points (only points without instructions). "
                    + "Use calc_points=false and instructions=false to disable point and instruction calculation");
        }

        boolean tmpElevation = ghRequest.getHints().getBool("elevation", elevation);

        String places = "";
        for (GHPoint p : ghRequest.getPoints()) {
            places += "&point=" + round6(p.lat) + "," + round6(p.lon);
        }

        String type = ghRequest.getHints().getString("type", "json");

        String url = routeServiceUrl
                + "?"
                + "profile=" + ghRequest.getProfile()
                + places
                + "&type=" + type
                + "&instructions=" + tmpInstructions
                + "&points_encoded=true"
                + "&points_encoded_multiplier=1000000"
                + "&calc_points=" + tmpCalcPoints
                + "&algorithm=" + ghRequest.getAlgorithm()
                + "&locale=" + ghRequest.getLocale().toString()
                + "&elevation=" + tmpElevation
                + "&optimize=" + tmpOptimize;

        for (String details : ghRequest.getPathDetails()) {
            url += "&" + Parameters.Details.PATH_DETAILS + "=" + details;
        }

        // append *all* point hints if at least one is not empty
        if (ghRequest.getPointHints().stream().anyMatch(h -> !h.isEmpty()))
            for (String hint : ghRequest.getPointHints())
                url += "&" + Parameters.Routing.POINT_HINT + "=" + encodeURL(hint);


        // append *all* curbsides if at least one is not empty
        if (ghRequest.getCurbsides().stream().anyMatch(c -> !c.isEmpty()))
            for (String curbside : ghRequest.getCurbsides())
                url += "&" + Parameters.Routing.CURBSIDE + "=" + encodeURL(curbside);

        // append *all* headings only if at least *one* is not NaN
        if (ghRequest.getHeadings().stream().anyMatch(h -> !Double.isNaN(h)))
            for (Double heading : ghRequest.getHeadings())
                url += "&heading=" + heading;


        for (String snapPrevention : ghRequest.getSnapPreventions()) {
            url += "&" + Parameters.Routing.SNAP_PREVENTION + "=" + encodeURL(snapPrevention);
        }

        if (!key.isEmpty()) {
            url += "&key=" + encodeURL(key);
        }

        for (Map.Entry<String, Object> entry : ghRequest.getHints().toMap().entrySet()) {
            String urlKey = entry.getKey();
            String urlValue = entry.getValue().toString();

            // use lower case conversion for check only!
            if (ignoreSetForGet.contains(toLowerCase(urlKey))) {
                continue;
            }

            if (urlValue != null && !urlValue.isEmpty()) {
                url += "&" + encodeURL(urlKey) + "=" + encodeURL(urlValue);
            }
        }

        return new Request.Builder().url(url)
                .header(X_GH_CLIENT_VERSION, GH_VERSION_FROM_MAVEN)
                .build();
    }

    public String export(GHRequest ghRequest) {
        String str = "Creating request failed";
        try {
            if (postRequest)
                throw new IllegalArgumentException("GPX export only works for GET requests, make sure to use `setPostRequest(false)`");
            Request okRequest = createGetRequest(ghRequest);
            str = getClientForRequest(ghRequest).newCall(okRequest).execute().body().string();

            return str;
        } catch (Exception ex) {
            throw new RuntimeException("Problem while fetching export " + ghRequest.getPoints()
                    + ", error: " + ex.getMessage() + " response: " + str, ex);
        }
    }

    private ArrayNode createStringList(List<String> list) {
        ArrayNode outList = objectMapper.createArrayNode();
        for (String str : list) {
            outList.add(str);
        }
        return outList;
    }

    private ArrayNode createDoubleList(List<Double> list) {
        ArrayNode outList = objectMapper.createArrayNode();
        for (Double d : list) {
            outList.add(d);
        }
        return outList;
    }

    private ArrayNode createPointList(List<GHPoint> list) {
        ArrayNode outList = objectMapper.createArrayNode();
        for (GHPoint p : list) {
            ArrayNode entry = objectMapper.createArrayNode();
            entry.add(p.lon);
            entry.add(p.lat);
            outList.add(entry);
        }
        return outList;
    }

    private static String encodeURL(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
