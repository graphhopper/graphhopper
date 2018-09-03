package com.graphhopper.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Karich
 */
public class GoogleMatrixSyncRequester extends GHMatrixAbstractRequester {

    public GoogleMatrixSyncRequester(String serviceUrl) {
        super(serviceUrl);
        initIgnore();
    }

    private void initIgnore() {
        ignoreSet.add("mode");
        ignoreSet.add("units");
        ignoreSet.add("destinations");
        ignoreSet.add("origins");
        ignoreSet.add("mode");
    }

    @Override
    public MatrixResponse route(GHMRequest ghRequest) {
        String pointsStr;

        pointsStr = createGoogleQuery(ghRequest.getFromPoints(), "origins");
        pointsStr += "&" + createGoogleQuery(ghRequest.getToPoints(), "destinations");

        List<String> outArraysList = new ArrayList<>(ghRequest.getOutArrays());
        if (outArraysList.isEmpty()) {
            // different default as google does not support weights
            outArraysList.add("distances");
            outArraysList.add("times");
        }

        // do not do the mapping here!
        // bicycling -> bike, car -> car, walking -> foot
        //
        String url = buildURL("", ghRequest);
        url += "&" + pointsStr + "&mode=" + ghRequest.getVehicle();

        boolean withTimes = outArraysList.contains("times");
        boolean withDistances = outArraysList.contains("distances");
        boolean withWeights = outArraysList.contains("weights");
        if (withWeights) {
            throw new UnsupportedOperationException("Google Matrix API does not include weights");
        }

        MatrixResponse matrixResponse = new MatrixResponse(
                ghRequest.getFromPoints().size(),
                ghRequest.getToPoints().size(), withTimes, withDistances, false);

        try {
            String str = getJson(url);
            JsonNode getResponseJson = objectMapper.reader().readTree(str);
            fillResponseFromJson(matrixResponse, getResponseJson);

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return matrixResponse;
    }

    private String createGoogleQuery(List<GHPoint> list, String pointName) {
        String pointsStr = "";
        for (GHPoint p : list) {
            if (!pointsStr.isEmpty()) {
                pointsStr += "|";
            }

            pointsStr += encode(Helper.round6(p.lat) + "," + Helper.round6(p.lon));
        }
        return pointName + "=" + pointsStr;
    }

    @Override
    public void fillResponseFromJson(MatrixResponse matrixResponse, String responseAsString) throws IOException {
        fillResponseFromJson(matrixResponse, objectMapper.reader().readTree(responseAsString));
    }

    @Override
    protected void fillResponseFromJson(MatrixResponse matrixResponse, JsonNode responseJson) {
        String status = responseJson.get("status").asText();
        if ("OK".equals(status)) {
            if (!responseJson.has("rows")) {
                matrixResponse.addError(new RuntimeException("No 'rows' entry found in Google Matrix response. status:OK"));
                return;
            }

            JsonNode rows = responseJson.get("rows");
            int fromCount = rows.size();

            for (int fromIndex = 0; fromIndex < fromCount; fromIndex++) {
                JsonNode elementsObj = rows.get(fromIndex);
                JsonNode elements = elementsObj.get("elements");
                int toCount = elements.size();
                long[] times = new long[toCount];
                int[] distances = new int[toCount];

                for (int toIndex = 0; toIndex < toCount; toIndex++) {
                    JsonNode element = elements.get(toIndex);

                    if ("OK".equals(element.get("status").asText())) {
                        JsonNode distance = element.get("distance");
                        JsonNode duration = element.get("duration");

                        times[toIndex] = duration.get("value").asLong() * 1000;
                        distances[toIndex] = Math.round(distance.get("value").asLong());
                    } else {
                        matrixResponse.addError(new IllegalArgumentException("Cannot find route " + fromIndex + "->" + toIndex));
                    }
                }

                matrixResponse.setTimeRow(fromIndex, times);
                matrixResponse.setDistanceRow(fromIndex, distances);
            }
        } else if (responseJson.has("error_message")) {
            matrixResponse.addError(new RuntimeException(responseJson.get("error_message").asText()));
        } else {
            matrixResponse.addError(new RuntimeException("Something went wrong with Google Matrix response. status:" + status));
        }
    }
}
