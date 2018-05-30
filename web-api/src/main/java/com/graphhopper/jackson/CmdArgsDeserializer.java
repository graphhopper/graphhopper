package com.graphhopper.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.util.CmdArgs;

import java.io.IOException;
import java.util.Map;

public class CmdArgsDeserializer extends JsonDeserializer<CmdArgs> {

    private static final TypeReference<Map<String,String>> MAP_STRING_STRING
            = new TypeReference<Map<String,String>>() {};

    @Override
    public CmdArgs deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        jsonParser.setCodec(new ObjectMapper());
        Map<String, String> args = jsonParser.readValueAs(MAP_STRING_STRING);
        return new CmdArgs(args);
    }
}
