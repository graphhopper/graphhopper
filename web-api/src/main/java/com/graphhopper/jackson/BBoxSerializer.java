package com.graphhopper.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.graphhopper.util.shapes.BBox;

import java.io.IOException;

class BBoxSerializer extends JsonSerializer<BBox> {
    @Override
    public void serialize(BBox bBox, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
        jsonGenerator.writeStartArray();
        for (Double number : bBox.toGeoJson()) {
            jsonGenerator.writeNumber(number);
        }
        jsonGenerator.writeEndArray();
    }
}
