package com.graphhopper.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Peter Karich
 */
public class GHMatrixSyncRequester extends GHMatrixAbstractRequester {

    public GHMatrixSyncRequester() {
        super();
        initIgnore();
    }

    public GHMatrixSyncRequester(String serviceUrl, OkHttpClient client) {
        super(serviceUrl, client);
        initIgnore();
    }

    public GHMatrixSyncRequester(String serviceUrl) {
        super(serviceUrl, new OkHttpClient.Builder().
                connectTimeout(15, TimeUnit.SECONDS).
                readTimeout(15, TimeUnit.SECONDS).build());
        initIgnore();
    }

    private void initIgnore() {
        ignoreSet.add("vehicle");
        ignoreSet.add("point");
        ignoreSet.add("from_point");
        ignoreSet.add("to_point");
        ignoreSet.add("add_array");
    }

    @Override
    public MatrixResponse route(GHMRequest ghRequest) {
        String pointsStr;
        if (ghRequest.identicalLists) {
            pointsStr = createPointQuery(ghRequest.getFromPoints(), "point");
        } else {
            pointsStr = createPointQuery(ghRequest.getFromPoints(), "from_point");
            pointsStr += "&" + createPointQuery(ghRequest.getToPoints(), "to_point");
        }

        String outArrayStr = "";
        List<String> outArraysList = new ArrayList<>(ghRequest.getOutArrays());
        if (outArraysList.isEmpty()) {
            outArraysList.add("weights");
        }

        for (String type : outArraysList) {
            if (!type.isEmpty()) {
                outArrayStr += "&";
            }

            outArrayStr += "out_array=" + type;
        }

        String url = buildURL("", ghRequest);
        url += "&" + pointsStr + "&" + outArrayStr + "&vehicle=" + ghRequest.getVehicle();

        boolean withTimes = outArraysList.contains("times");
        boolean withDistances = outArraysList.contains("distances");
        boolean withWeights = outArraysList.contains("weights");
        MatrixResponse matrixResponse = new MatrixResponse(
                ghRequest.getFromPoints().size(),
                ghRequest.getToPoints().size(), withTimes, withDistances, withWeights);

        try {
            String str = getJson(url);
            JsonNode getResponseJson = objectMapper.reader().readTree(str);

            matrixResponse.addErrors(readErrors(getResponseJson));
            if (!matrixResponse.hasErrors()) {
                matrixResponse.addErrors(readUsableEntityError(outArraysList, getResponseJson));
            }

            if (!matrixResponse.hasErrors())
                fillResponseFromJson(matrixResponse, getResponseJson);

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return matrixResponse;
    }

    private String createPointQuery(List<GHPoint> list, String pointName) {
        String pointsStr = "";
        for (GHPoint p : list) {
            if (!pointsStr.isEmpty()) {
                pointsStr += "&";
            }

            pointsStr += pointName + "=" + encode(Helper.round6(p.lat) + "," + Helper.round6(p.lon));
        }
        return pointsStr;
    }
}
