package com.graphhopper.jackson;

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.Helper;
import com.graphhopper.util.JsonFeature;
import com.graphhopper.util.JsonFeatureCollection;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

public class CustomModelAreasDeserializer extends JsonDeserializer<JsonFeatureCollection> {

    @Override
    public JsonFeatureCollection deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode treeNode = jp.readValueAsTree();
        JsonFeatureCollection collection = new JsonFeatureCollection();

        if (treeNode.has("type") && "FeatureCollection".equals(treeNode.get("type").asText())) {
            // Unfortunately the simpler code ala "jp.getCodec().treeToValue(treeNode, JsonFeatureCollection.class)" results in a StackErrorException
            for (JsonNode node : treeNode.get("features")) {
                JsonFeature feature = jp.getCodec().treeToValue(node, JsonFeature.class);
                if (Helper.isEmpty(feature.getId()))
                    throw new IllegalArgumentException("The JsonFeature for the CustomModel area must contain \"id\"");
                collection.getFeatures().add(feature);
            }
        } else {
            Iterator<Map.Entry<String, JsonNode>> fields = treeNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonFeature feature = jp.getCodec().treeToValue(field.getValue(), JsonFeature.class);
                feature.setId(field.getKey());
                collection.getFeatures().add(feature);
            }
        }

        // duplicate "id" check
        Map<String, JsonFeature> index = CustomModel.getAreasAsMap(collection);
        if (index.size() != collection.getFeatures().size()) // redundant but cannot hurt
            throw new IllegalArgumentException("JsonFeatureCollection contains duplicate area");
        return collection;
    }
}
