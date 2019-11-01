package com.graphhopper.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.jackson.PathWrapperDeserializer;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Karich
 */
public class GHMatrixSyncRequester extends GHMatrixAbstractRequester {

    public GHMatrixSyncRequester() {
        super();
        initIgnore();
    }

    public GHMatrixSyncRequester(String serviceUrl) {
        super(serviceUrl);
        initIgnore();
    }

    public GHMatrixSyncRequester(String serviceUrl, OkHttpClient client) {
        super(serviceUrl, client);
        initIgnore();
    }

    private void initIgnore() {
        ignoreSet.add("vehicle");
        ignoreSet.add("point");
        ignoreSet.add("from_point");
        ignoreSet.add("from_point_hint");
        ignoreSet.add("to_point");
        ignoreSet.add("to_point_hint");
        ignoreSet.add("add_array");
    }

    @Override
    public MatrixResponse route(GHMRequest ghRequest) {
        String pointsStr;
        String pointHintsStr;
        String curbsidesStr;
        if (ghRequest.identicalLists) {
            pointsStr = createPointQuery("point", ghRequest.getFromPoints());
            pointHintsStr = createUrlString("point_hint", ghRequest.getFromPointHints());
            curbsidesStr = createUrlString("curbside", ghRequest.getFromCurbsides());
        } else {
            pointsStr = createPointQuery("from_point", ghRequest.getFromPoints());
            pointsStr += "&" + createPointQuery("to_point", ghRequest.getToPoints());

            pointHintsStr = createUrlString("from_point_hint", ghRequest.getFromPointHints());
            pointHintsStr += "&" + createUrlString("to_point_hint", ghRequest.getToPointHints());

            curbsidesStr = createUrlString("from_curbside", ghRequest.getFromCurbsides());
            curbsidesStr += "&" + createUrlString("to_curbside", ghRequest.getToCurbsides());
        }

        String outArrayStr = "";
        List<String> outArraysList = new ArrayList<>(ghRequest.getOutArrays());
        if (outArraysList.isEmpty()) {
            outArraysList.add("weights");
        }

        for (String type : outArraysList) {
            if (!type.isEmpty())
                outArrayStr += "&";

            outArrayStr += "out_array=" + type;
        }

        String snapPreventionStr = "";
        for (String snapPrevention : ghRequest.getSnapPreventions()) {
            if (!snapPreventionStr.isEmpty())
                snapPreventionStr += "&";

            snapPreventionStr += "snap_prevention=" + snapPrevention;
        }

        String url = buildURL("", ghRequest);
        url += "&" + pointsStr + "&" + pointHintsStr + "&" + curbsidesStr + "&" + outArrayStr + "&" + snapPreventionStr;
        if (!Helper.isEmpty(ghRequest.getVehicle())) {
            url += "&vehicle=" + ghRequest.getVehicle();
        }
        url += "&fail_fast=" + ghRequest.getFailFast();

        boolean withTimes = outArraysList.contains("times");
        boolean withDistances = outArraysList.contains("distances");
        boolean withWeights = outArraysList.contains("weights");
        MatrixResponse matrixResponse = new MatrixResponse(
                ghRequest.getFromPoints().size(),
                ghRequest.getToPoints().size(), withTimes, withDistances, withWeights);

        try {
            String str = getJson(url);
            JsonNode getResponseJson = objectMapper.reader().readTree(str);

            matrixResponse.addErrors(PathWrapperDeserializer.readErrors(objectMapper, getResponseJson));
            if (!matrixResponse.hasErrors()) {
                matrixResponse.addErrors(readUsableEntityError(outArraysList, getResponseJson));
            }

            if (!matrixResponse.hasErrors())
                fillResponseFromJson(matrixResponse, getResponseJson, ghRequest.getFailFast());

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return matrixResponse;
    }

    private String createUrlString(String paramName, List<String> params) {
        StringBuilder result = new StringBuilder();
        for (String param : params) {
            if (result.length() > 0)
                result.append("&");
            result.append(paramName).append('=').append(encode(param));
        }
        return result.toString();
    }

    private String createPointQuery(String pointName, List<GHPoint> list) {
        StringBuilder pointsStr = new StringBuilder();
        for (GHPoint p : list) {
            if (pointsStr.length() > 0)
                pointsStr.append("&");

            pointsStr.append(pointName).append('=').append(encode(Helper.round6(p.lat) + "," + Helper.round6(p.lon)));
        }
        return pointsStr.toString();
    }
}
