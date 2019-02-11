package com.graphhopper.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.graphhopper.util.shapes.GHPoint;

import java.io.IOException;

class GHPointSerializer extends JsonSerializer<GHPoint> {
    @Override
    public void serialize(GHPoint ghPoint, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
        jsonGenerator.writeStartArray();
        for (Double number : ghPoint.toGeoJson()) {
            jsonGenerator.writeNumber(number);
        }
        jsonGenerator.writeEndArray();
    }
}
