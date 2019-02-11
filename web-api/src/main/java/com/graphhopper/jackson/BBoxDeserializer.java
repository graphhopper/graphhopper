package com.graphhopper.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.graphhopper.util.shapes.BBox;

import java.io.IOException;

class BBoxDeserializer extends JsonDeserializer<BBox> {
    @Override
    public BBox deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        double[] bounds = jsonParser.readValueAs(double[].class);
        return new BBox(bounds);
    }
}
