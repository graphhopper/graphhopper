package com.graphhopper.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.graphhopper.util.flex.FlexModel;

import java.io.IOException;

public class FlexModelDeserializer extends JsonDeserializer<FlexModel> {
    @Override
    public FlexModel deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        p.setCodec(new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE));
        return p.readValueAs(FlexModel.class);
    }
}
