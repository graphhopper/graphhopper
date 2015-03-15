package com.graphhopper;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.graphhopper.bean.RoutePoint;
import com.graphhopper.client.DesktopHopperClient;

import java.io.IOException;
import java.util.Random;

public class Example {

    public static void main(String[] args) throws IOException {
        // Note: I made a new *Hopper* naming convention,
        // 'cause "GraphHopperSomething" is TOO much long
        // and "GHSomething" is mostly unreadable

        HopperResponse res = new DesktopHopperClient()
                .route(new HopperRequest(52.47379, 13.362808, 52.4736925, 13.3904394));

        boolean severalHundredRequestsPerSecond = new Random().nextBoolean();

        double lat = 0, lon = 0;
        if(severalHundredRequestsPerSecond) {
            // For advanced people with GC concerns
            for(double[] point : res.getCoordinates()) {
                lat = point[0];
                lon = point[1];
            }
        } else {
            // For normal people
            for(RoutePoint point : res.getPoints()) {
                lat = point.getLatitude();
                lon = point.getLongitude();
            }
        }

        System.out.println("Paranoic Mode: " + severalHundredRequestsPerSecond);
        System.out.println(lat + " " + lon);
        System.out.println(res.getBbox().getMaxLatitude());
        System.out.println(res.getPolyline());

        // Serialization Example
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);// Optional

        // ObjectToJson
        String jsonString = mapper.writeValueAsString(res);
        System.out.println(jsonString);

        // JsonToObject (yes, the web-client can be almost fired now :)
        HopperResponse serializerResponse = mapper.readValue(jsonString, HopperResponse.class);
        System.out.println("From JSON: " + serializerResponse.getDistance());
    }
}
