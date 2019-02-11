package com.graphhopper.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.graphhopper.util.shapes.GHPoint;

import java.io.IOException;

class GHPointDeserializer extends JsonDeserializer<GHPoint> {
    @Override
    public GHPoint deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        double[] bounds = jsonParser.readValueAs(double[].class);
        return GHPoint.fromJson(bounds);
    }
}
