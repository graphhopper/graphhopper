package com.graphhopper;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.graphhopper.bean.RouteInstruction;
import com.graphhopper.client.DesktopHopperClient;
import com.graphhopper.engine.FileHopperEngine;

import java.io.IOException;

public class Example {

    public static void main(String[] args) throws IOException {
        // Step 1: Create an Engine
        HopperEngine engine = new FileHopperEngine("italy.osm");

        // Step 2: Create a Client
        // It will inizialize the engine for the user, see AbstractHopperClient constructor
        HopperClient client = new DesktopHopperClient(engine);

        // Step 3: Use it
        HopperResponse res = client.route(new HopperRequest(52.47379, 13.362808, 52.4736925, 13.3904394));

        // Step 4: Traverse the response
        for(RouteInstruction instruction : res.getInstructions()) {
            System.out.println(instruction.getText());
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
}
