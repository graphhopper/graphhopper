package com.graphhopper.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.util.shapes.GHPoint;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Peter Karich
 */
public class GHMatrixBatchRequester extends GHMatrixAbstractRequester {
    final JsonNodeFactory factory = JsonNodeFactory.instance;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private int maxIterations = 100;
    private long sleepAfterGET = 1000;

    public GHMatrixBatchRequester() {
        super();
    }

    public GHMatrixBatchRequester(String serviceUrl) {
        super(serviceUrl);
    }

    public GHMatrixBatchRequester(String serviceUrl, OkHttpClient client) {
        super(serviceUrl, client);
    }

    /**
     * Internal parameter. Increase only if you have very large matrices.
     */
    public GHMatrixBatchRequester setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    /**
     * Internal parameter. Increase only if you have very large matrices.
     */
    public GHMatrixBatchRequester setSleepAfterGET(long sleepAfterGETMillis) {
        this.sleepAfterGET = sleepAfterGETMillis;
        return this;
    }

    @Override
    public MatrixResponse route(GHMRequest ghRequest) {
        ObjectNode requestJson = factory.objectNode();

        List<String> outArraysList = new ArrayList<>(ghRequest.getOutArrays());
        if (outArraysList.isEmpty()) {
            outArraysList.add("weights");
        }

        ArrayNode outArrayListJson = factory.arrayNode();
        for (String str : outArraysList) {
            outArrayListJson.add(str);
        }

        boolean hasElevation = false;
        if (ghRequest.identicalLists) {
            requestJson.putArray("points").addAll(createPointList(ghRequest.getFromPoints()));
            requestJson.putArray("point_hints").addAll(createStringList(ghRequest.getFromPointHints()));
        } else {
            ArrayNode fromPointList = createPointList(ghRequest.getFromPoints());
            ArrayNode toPointList = createPointList(ghRequest.getToPoints());
            requestJson.putArray("from_points").addAll(fromPointList);
            requestJson.putArray("from_point_hints").addAll(createStringList(ghRequest.getFromPointHints()));
            requestJson.putArray("to_points").addAll(toPointList);
            requestJson.putArray("to_point_hints").addAll(createStringList(ghRequest.getToPointHints()));
        }

        requestJson.putArray("out_arrays").addAll(outArrayListJson);
        requestJson.put("vehicle", ghRequest.getVehicle());
        requestJson.put("elevation", hasElevation);

        Map<String, String> hintsMap = ghRequest.getHints().toMap();
        for (String hintKey : hintsMap.keySet()) {
            if (ignoreSet.contains(hintKey))
                continue;

            String hint = hintsMap.get(hintKey);
            requestJson.put(hintKey, hint);
        }

        boolean withTimes = outArraysList.contains("times");
        boolean withDistances = outArraysList.contains("distances");
        boolean withWeights = outArraysList.contains("weights");
        final MatrixResponse matrixResponse = new MatrixResponse(
                ghRequest.getFromPoints().size(),
                ghRequest.getToPoints().size(), withTimes, withDistances, withWeights);

        String postUrl = buildURLNoHints("/calculate", ghRequest);

        try {
            String postResponseStr = postJson(postUrl, requestJson);
            boolean debug = ghRequest.getHints().getBool("debug", false);
            if (debug) {
                logger.info("POST URL:" + postUrl + ", request:" + requestJson + ", response: " + postResponseStr);
            }

            JsonNode responseJson = toJSON(postUrl, postResponseStr);
            if (responseJson.has("message")) {
                matrixResponse.addErrors(readErrors(responseJson));
                return matrixResponse;
            }
            if (!responseJson.has("job_id")) {
                throw new IllegalStateException("Response should contain job_id but was "
                        + postResponseStr + ", json:" + requestJson + ",url:" + postUrl);
            }

            final String id = responseJson.get("job_id").asText();
            int i = 0;
            for (; i < maxIterations; i++) {
                // SLEEP a bit and GET solution
                if (sleepAfterGET > 0) {
                    Thread.sleep(sleepAfterGET);
                }
                String getUrl = buildURLNoHints("/solution/" + id, ghRequest);

                String getResponseStr;
                try {
                    getResponseStr = getJson(getUrl);
                } catch (SocketTimeoutException ex) {
                    // if timeout exception try once again:
                    getResponseStr = getJson(getUrl);
                }

                JsonNode getResponseJson = toJSON(getUrl, getResponseStr);
                if (debug) {
                    logger.info(i + " GET URL:" + getUrl + ", response: " + getResponseStr);
                }
                matrixResponse.addErrors(readErrors(getResponseJson));
                if (matrixResponse.hasErrors()) {
                    break;
                }
                String status = getResponseJson.get("status").asText();

                if ("processing".equals(status) || "waiting".equals(status)) {
                    continue;
                }

                if ("finished".equals(status)) {
                    JsonNode solution = getResponseJson.get("solution");
                    matrixResponse.addErrors(readUsableEntityError(outArraysList, solution));
                    if (!matrixResponse.hasErrors())
                        fillResponseFromJson(matrixResponse, solution);

                    break;
                }

                matrixResponse.addError(new RuntimeException("Status not supported: " + status + " - illegal JSON format?"));
                break;
            }

            if (i >= maxIterations) {
                throw new IllegalStateException("Maximum number of iterations reached " + maxIterations + ", increasing should only be necessary for big matrices. For smaller ones this is a bug, please contact us");
            }

        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return matrixResponse;
    }

    private final ArrayNode createStringList(List<String> list) {
        ArrayNode outList = factory.arrayNode();
        for (String str : list) {
            outList.add(str);
        }
        return outList;
    }

    protected final ArrayNode createPointList(List<GHPoint> list) {
        ArrayNode outList = factory.arrayNode();
        for (GHPoint p : list) {
            ArrayNode entry = factory.arrayNode();
            entry.add(p.lon);
            entry.add(p.lat);
            outList.add(entry);
        }
        return outList;
    }
}
