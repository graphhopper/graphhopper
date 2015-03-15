package com.graphhopper.client;


import com.graphhopper.GHResponse;
import com.graphhopper.HopperClient;
import com.graphhopper.HopperRequest;
import com.graphhopper.HopperResponse;
import com.graphhopper.bean.RouteBbox;
import com.graphhopper.bean.RouteInstruction;
import com.graphhopper.http.GraphHopperWeb;
import com.graphhopper.http.WebHelper;
import com.graphhopper.internal.HopperEngine;
import com.graphhopper.internal.LazyPointList;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Please do not throw an error if hasError (with check everywhere)
// Throws errors on errors is an anti-pattern
public abstract class AbstractHopperClient implements HopperClient {

    // All child-classes must provide a customized engine
    public abstract HopperEngine getEngine();

    @Override
    public HopperResponse route(HopperRequest request) {
        /**
         * This method is a mess, and I cheated alot here using the downloader.
         * For real the method must take in consideration the "fetchXXX" from HopperRequest
         * and use the HopperEngine to generate the info and populate the beans.
         *
         * Note that the refactor must GO DEEPER (using the beans) for real performance improvements.
         *
         * DEPP REFACTOR TODO:
         * - com.graphhopper.util.PathMerger (can be merged inside here, actually)
         * - com.graphhopper.routing.Path
         */
        Downloader downloader = new Downloader("GraphHopper Test") {
            @Override
            public InputStream fetch( String url ) throws IOException {
                return getClass().getClassLoader().getResourceAsStream("com.graphhopper/test.json");
            }
        };

        GraphHopperWeb instance = new GraphHopperWeb().setPointsEncoded(false);
        instance.setDownloader(downloader);
        GHResponse response = instance.route(request);

        // Populating
        HopperResponse routeResponse = new HopperResponse();
        routeResponse.setDistance(Helper.round(response.getDistance(), 3));
        routeResponse.setWeight(Helper.round6(response.getDistance()));
        routeResponse.setTime(response.getMillis());

        BBox bbList = response.calcRouteBBox(new BBox(0, 0, 0, 0));
        RouteBbox bbox = new RouteBbox();
        bbox.setMaxLatitude(bbList.maxLat);
        bbox.setMinLatitude(bbList.minLat);
        bbox.setMaxLongitude(bbList.maxLon);
        bbox.setMinLongitude(bbList.minLon);
        routeResponse.setBbox(bbox);

        PointList pointList = response.getPoints();

        List<double[]> coordinates = getCoordinates(pointList);
        routeResponse.setPolyline(WebHelper.encodePolyline(pointList));
        routeResponse.setCoordinates(coordinates);
        routeResponse.setPoints(new LazyPointList(coordinates));


        // Paths.Instructions[]
        List<RouteInstruction> routeInstructionList = new ArrayList<RouteInstruction>(response.getInstructions().size());
        InstructionList responseInstructions = response.getInstructions();
        if (responseInstructions != null && !responseInstructions.isEmpty()) {
            List<Map<String, Object>> createJson = responseInstructions.createJson();
            for (int i = 0; i < createJson.size(); i++) {
                Map<String, Object> jsonInstruction = createJson.get(i);
                RouteInstruction instruction = new RouteInstruction();
                instruction.setDistance((Double) jsonInstruction.get("distance"));
                instruction.setSign((Integer) jsonInstruction.get("sign"));
                instruction.setTime((Long) jsonInstruction.get("time"));
                instruction.setText((String) jsonInstruction.get("text"));

                //noinspection unchecked
                List<Integer> interval = (List<Integer>) jsonInstruction.get("interval");
                instruction.setInterval(new int[]{interval.get(0), interval.get(1)});

                if (jsonInstruction.containsKey("annotation_text") && jsonInstruction.containsKey("annotation_importance")) {
                    instruction.setAnnotationText((String) jsonInstruction.get("annotation_text"));
                    instruction.setAnnotationImportance((Integer) jsonInstruction.get("annotation_importance"));
                }

                PointList pointList1 = response.getInstructions().get(i).getPoints();

                instruction.setPolyline(WebHelper.encodePolyline(pointList1));
                instruction.setPoints(new LazyPointList(coordinates.subList(interval.get(0), interval.get(1))));
                instruction.setCoordinates(coordinates.subList(interval.get(0), interval.get(1)));
                routeInstructionList.add(instruction);
            }
        }

        routeResponse.setInstructions(routeInstructionList);

        return routeResponse;
    }

    private List<double[]> getCoordinates(PointList pointList) {
        List<double[]> coordinates = new ArrayList<double[]>(pointList.size());

        for (int i = 0; i < pointList.size(); i++){
            /// wooooo very efficient :)
            coordinates.add(new double[] { pointList.toGeoJson().get(0)[0], pointList.toGeoJson().get(0)[1]});
        }

        return coordinates;
    }
}
