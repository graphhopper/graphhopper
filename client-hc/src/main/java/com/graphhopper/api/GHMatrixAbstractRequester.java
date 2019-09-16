package com.graphhopper.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.util.Helper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.graphhopper.api.GraphHopperMatrixWeb.*;

/**
 * @author Peter Karich
 */
public abstract class GHMatrixAbstractRequester {

    protected final ObjectMapper objectMapper;
    protected final Set<String> ignoreSet = new HashSet<>(10);
    protected final String serviceUrl;
    private OkHttpClient downloader;

    public GHMatrixAbstractRequester() {
        this("https://graphhopper.com/api/1/matrix");
    }

    public GHMatrixAbstractRequester(String serviceUrl) {
        this(serviceUrl, new OkHttpClient.Builder().
                connectTimeout(5, TimeUnit.SECONDS).
                readTimeout(5, TimeUnit.SECONDS).build());
    }

    public GHMatrixAbstractRequester(String serviceUrl, OkHttpClient downloader) {
        if (serviceUrl.endsWith("/")) {
            serviceUrl = serviceUrl.substring(0, serviceUrl.length() - 1);
        }
        this.downloader = downloader;
        this.serviceUrl = serviceUrl;

        ignoreSet.add("key");
        ignoreSet.add("service_url");
        this.objectMapper = new ObjectMapper();
    }

    public abstract MatrixResponse route(GHMRequest request);

    public GHMatrixAbstractRequester setDownloader(OkHttpClient downloader) {
        this.downloader = downloader;
        return this;
    }

    public OkHttpClient getDownloader() {
        return downloader;
    }

    protected String getJson(String url) throws IOException {
        Request okRequest = new Request.Builder().url(url).build();
        ResponseBody body = null;
        try {
            body = downloader.newCall(okRequest).execute().body();
            return body.string();
        } finally {
            Helper.close(body);
        }
    }

    protected String postJson(String url, JsonNode data) throws IOException {
        Request okRequest = new Request.Builder().url(url).post(RequestBody.create(MT_JSON, data.toString())).build();
        ResponseBody body = null;
        try {
            body = downloader.newCall(okRequest).execute().body();
            return body.string();
        } finally {
            Helper.close(body);
        }
    }

    protected JsonNode toJSON(String url, String str) {
        try {
            return objectMapper.readTree(str);
        } catch (Exception ex) {
            throw new RuntimeException("Cannot parse json " + str + " from " + url);
        }
    }

    public List<Throwable> readUsableEntityError(List<String> outArraysList, JsonNode solution) {
        boolean readWeights = outArraysList.contains("weights") && solution.has("weights");
        boolean readDistances = outArraysList.contains("distances") && solution.has("distances");
        boolean readTimes = outArraysList.contains("times") && solution.has("times");

        if (!readWeights && !readDistances && !readTimes) {
            return Collections.<Throwable>singletonList(new RuntimeException("Cannot find usable entity like weights, distances or times in JSON"));
        } else {
            return Collections.emptyList();
        }
    }

    public void fillResponseFromJson(MatrixResponse matrixResponse, String responseAsString, boolean failFast) throws IOException {
        fillResponseFromJson(matrixResponse, objectMapper.reader().readTree(responseAsString), failFast);
    }

    /**
     * @param failFast If false weights/distances/times that are null are interpreted as disconnected points and are
     *                 thus set to their respective maximum values. Furthermore, the indices of the disconnected points
     *                 are added to {@link MatrixResponse#getDisconnectedPoints()} and the indices of the points that
     *                 could not be found are added to {@link MatrixResponse#getInvalidFromPoints()} and/or
     *                 {@link MatrixResponse#getInvalidToPoints()}.
     */
    protected void fillResponseFromJson(MatrixResponse matrixResponse, JsonNode solution, boolean failFast) {
        final boolean readWeights = solution.has("weights");
        final boolean readDistances = solution.has("distances");
        final boolean readTimes = solution.has("times");

        int fromCount = 0;
        JsonNode weightsArray = null;
        if (readWeights) {
            weightsArray = solution.get("weights");
            fromCount = checkArraySizes("weights", weightsArray.size());
        }
        JsonNode timesArray = null;
        if (readTimes) {
            timesArray = solution.get("times");
            fromCount = checkArraySizes("times", timesArray.size(), weightsArray);
        }
        JsonNode distancesArray = null;
        if (readDistances) {
            distancesArray = solution.get("distances");
            fromCount = checkArraySizes("distances", distancesArray.size(), weightsArray, timesArray);
        }

        for (int fromIndex = 0; fromIndex < fromCount; fromIndex++) {
            int toCount = 0;
            JsonNode weightsFromArray = null;
            double[] weights = null;
            if (readWeights) {
                weightsFromArray = weightsArray.get(fromIndex);
                weights = new double[weightsFromArray.size()];
                toCount = checkArraySizes("weights", weightsFromArray.size());
            }

            JsonNode timesFromArray = null;
            long[] times = null;
            if (readTimes) {
                timesFromArray = timesArray.get(fromIndex);
                times = new long[timesFromArray.size()];
                toCount = checkArraySizes("times", timesFromArray.size(), weightsFromArray);
            }

            JsonNode distancesFromArray = null;
            int[] distances = null;
            if (readDistances) {
                distancesFromArray = distancesArray.get(fromIndex);
                distances = new int[distancesFromArray.size()];
                toCount = checkArraySizes("distances", distancesFromArray.size(), weightsFromArray, timesFromArray);
            }

            for (int toIndex = 0; toIndex < toCount; toIndex++) {
                if (readWeights) {
                    if (weightsFromArray.get(toIndex).isNull() && !failFast) {
                        weights[toIndex] = Double.MAX_VALUE;
                    } else {
                        weights[toIndex] = weightsFromArray.get(toIndex).asDouble();
                    }
                }

                if (readTimes) {
                    if (timesFromArray.get(toIndex).isNull() && !failFast) {
                        times[toIndex] = Long.MAX_VALUE;
                    } else {
                        times[toIndex] = timesFromArray.get(toIndex).asLong() * 1000;
                    }
                }

                if (readDistances) {
                    if (distancesFromArray.get(toIndex).isNull() && !failFast) {
                        distances[toIndex] = Integer.MAX_VALUE;
                    } else {
                        distances[toIndex] = (int) Math.round(distancesFromArray.get(toIndex).asDouble());
                    }
                }
            }

            if (readWeights) {
                matrixResponse.setWeightRow(fromIndex, weights);
            }

            if (readTimes) {
                matrixResponse.setTimeRow(fromIndex, times);
            }

            if (readDistances) {
                matrixResponse.setDistanceRow(fromIndex, distances);
            }
        }
        if (!failFast && solution.has("hints")) {
            addProblems(matrixResponse, solution.get("hints"));
        }
    }

    private void addProblems(MatrixResponse matrixResponse, JsonNode hints) {
        for (JsonNode hint : hints) {
            if (hint.has("point_pairs")) {
                matrixResponse.setDisconnectedPoints(readDisconnectedPoints(hint.get("point_pairs")));
            }
            if (hint.has("invalid_from_points")) {
                matrixResponse.setInvalidFromPoints(readInvalidPoints(hint.get("invalid_from_points")));
                matrixResponse.setInvalidToPoints(readInvalidPoints(hint.get("invalid_to_points")));
            }
        }
    }

    private List<MatrixResponse.PointPair> readDisconnectedPoints(JsonNode pointPairsArray) {
        List<MatrixResponse.PointPair> disconnectedPoints = new ArrayList<>(pointPairsArray.size());
        for (int i = 0; i < pointPairsArray.size(); i++) {
            if (pointPairsArray.get(i).size() != 2) {
                throw new IllegalArgumentException("all point_pairs are expected to contain two elements");
            }
            disconnectedPoints.add(new MatrixResponse.PointPair(
                    pointPairsArray.get(i).get(0).asInt(),
                    pointPairsArray.get(i).get(1).asInt()
            ));
        }
        return disconnectedPoints;
    }

    private List<Integer> readInvalidPoints(JsonNode pointsArray) {
        List<Integer> result = new ArrayList<>(pointsArray.size());
        for (int i = 0; i < pointsArray.size(); i++) {
            result.add(pointsArray.get(i).asInt());
        }
        return result;
    }

    private static int checkArraySizes(String msg, int len, JsonNode... arrays) {
        for (JsonNode other : arrays) {
            if (len <= 0)
                throw new IllegalArgumentException("Size " + len + " of '" + msg + "' array is too small");

            if (other != null && len != other.size())
                throw new IllegalArgumentException("Size " + len + " of '" + msg + "' array is has to be equal to other arrays but wasn't");
        }
        return len;
    }

    protected String buildURLNoHints(String path, GHMRequest ghRequest) {
        // allow per request service URLs
        String tmpServiceURL = ghRequest.getHints().get(SERVICE_URL, serviceUrl);
        String url = tmpServiceURL;
        url += path + "?";

        String key = ghRequest.getHints().get(KEY, "");
        if (!Helper.isEmpty(key)) {
            url += "key=" + key;
        }
        return url;
    }

    protected String buildURL(String path, GHMRequest ghRequest) {
        String url = buildURLNoHints(path, ghRequest);
        for (Map.Entry<String, String> entry : ghRequest.getHints().toMap().entrySet()) {
            if (ignoreSet.contains(entry.getKey())) {
                continue;
            }

            url += "&" + encode(entry.getKey()) + "=" + encode(entry.getValue());
        }
        return url;
    }

    protected static String encode(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (Exception ex) {
            return str;
        }
    }

}
