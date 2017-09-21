package com.graphhopper.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoderFactory;

import java.io.IOException;

class EncodingManagerDeserializer extends JsonDeserializer<EncodingManager> {
    @Override
    public EncodingManager deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        final JsonNode node = parser.getCodec().readTree(parser);
        final ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        int extendedDataSize = node.get("extended_data_size").asInt();
        EncodingManager.Builder emBuilder = new EncodingManager.Builder(extendedDataSize);

        // 1. loop over encoded values
        for (JsonNode encNode : node.get("encoded_values")) {
            String classType = encNode.get("class_type").asText();
            switch (classType) {
                case "BooleanEncodedValue":
                    emBuilder.addEncodedValue(mapper.treeToValue(encNode, BooleanEncodedValue.class));
                    break;
                case "IntEncodedValue":
                    emBuilder.addEncodedValue(mapper.treeToValue(encNode, IntEncodedValue.class));
                    break;
                case "DecimalEncodedValue":
                    emBuilder.addEncodedValue(mapper.treeToValue(encNode, DecimalEncodedValue.class));
                    break;
                case "MappedEncodedValue":
                    emBuilder.addEncodedValue(mapper.treeToValue(encNode, MappedDecimalEncodedValue.class));
                    break;
                case "StringEncodedValue":
                    emBuilder.addEncodedValue(mapper.treeToValue(encNode, StringEncodedValue.class));
                    break;
                default:
                    throw new IllegalStateException("Unknown EncodedValue type " + classType);
            }
        }

        // 2. init old flag encoders; custom FlagEncoderFactory is only possible when configuring; do not add EncodedValues, just check for there existence
        // TODO NOW check config vs. serialized encoded-values.json somehow
        int bytesForEdgeFlags = node.get("bits_for_edge_flags").asInt() / 8;
        String fesString = node.get("flag_encoder_details_list").asText();
        emBuilder.addAll(FlagEncoderFactory.DEFAULT, fesString, bytesForEdgeFlags, false);
        return emBuilder.build();
    }
}
