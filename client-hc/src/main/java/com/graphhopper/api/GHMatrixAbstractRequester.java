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

    private GraphHopperWeb web = new GraphHopperWeb();
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

    public void fillResponseFromJson(MatrixResponse matrixResponse, String responseAsString) throws IOException {
        fillResponseFromJson(matrixResponse, objectMapper.reader().readTree(responseAsString));
    }

    protected void fillResponseFromJson(MatrixResponse matrixResponse, JsonNode solution) {
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
                    weights[toIndex] = weightsFromArray.get(toIndex).asDouble();
                }

                if (readTimes) {
                    times[toIndex] = timesFromArray.get(toIndex).asLong() * 1000;
                }

                if (readDistances) {
                    distances[toIndex] = (int) Math.round(distancesFromArray.get(toIndex).asDouble());
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

    public List<Throwable> readErrors(JsonNode json) {
        return web.readErrors(json);
    }
}
