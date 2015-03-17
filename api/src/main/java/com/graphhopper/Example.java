package com.graphhopper;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.graphhopper.bean.RouteBbox;
import com.graphhopper.bean.RouteInstruction;
import com.graphhopper.bean.RoutePoint;
import com.graphhopper.client.EmbeddedHopperClient;
import com.graphhopper.engine.FileHopperEngine;
import com.graphhopper.engine.configuration.MobileEngineConfiguration;
import com.graphhopper.http.GraphHopperWeb;
import com.graphhopper.http.WebHelper;
import com.graphhopper.internal.LazyPointList;
import com.graphhopper.util.Downloader;
import com.graphhopper.util.Helper;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("ForLoopReplaceableByForEach")
public class Example {

    public static void main(String[] args) throws IOException {
        // Step 1: Create an Engine and choose the good configuration
        HopperEngine engine = new FileHopperEngine("italy.osm").inizialize(new MobileEngineConfiguration());

        // Step 2: Create a Client
        // It will inizialize the engine for the user, see AbstractHopperClient constructor
        HopperClient client = new EmbeddedHopperClient(engine);

        // Step 3: Use it
        HopperRequest req = new HopperRequest(52.47379, 13.362808, 52.4736925, 13.3904394);
        HopperResponse res = /** client.route(req); **/ new Example().getFakeResponseForTestingSerialization(req);

        // Step 4.0: Traverse the response
        for(RouteInstruction instruction : res.getInstructions()) {
            System.out.println("Instruction Text: " + instruction.getText());
        }

        //Step 4.1 You can use List<RoutePoint>
        for(RoutePoint point : res.getPoints()) {
            System.out.println("Latitude from Point s" + point.getLatitude());
        }

        // Step 4.2 If the GC is a concern of yours, you can use the coordinates directly (no iterator())
        List<double[]> coordinates = res.getCoordinates();
        for(int i = 0, len = coordinates.size(); i < len ; i++) {
            double[] point = coordinates.get(i);
            System.out.println("Latitude from double[] " + point[0]);
        }

        // Step 5 [Optional]: Serialize ObjectToJson
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);// Optional
        String jsonString = mapper.writeValueAsString(res);
        System.out.println(jsonString);

        // JsonToObject
        HopperResponse serializerResponse = mapper.readValue(jsonString, HopperResponse.class);
        System.out.println("From JSON: " + serializerResponse.getDistance());
    }

    public HopperResponse getFakeResponseForTestingSerialization(HopperRequest request) {
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

    public List<double[]> getCoordinates(PointList pointList) {
        List<double[]> coordinates = new ArrayList<double[]>(pointList.size());

        for (int i = 0; i < pointList.size(); i++){
            /// wooooo very efficient :)
            coordinates.add(new double[] { pointList.toGeoJson().get(0)[0], pointList.toGeoJson().get(0)[1]});
        }

        return coordinates;
    }

}
