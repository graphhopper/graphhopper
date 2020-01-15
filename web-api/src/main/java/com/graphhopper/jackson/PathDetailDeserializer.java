package com.graphhopper.jackson;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.util.details.PathDetail;

import java.io.IOException;

public class PathDetailDeserializer extends JsonDeserializer<PathDetail> {

    @Override
    public PathDetail deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode pathDetail = jp.readValueAsTree();
        if (pathDetail.size() != 3)
            throw new JsonParseException(jp, "PathDetail array must have exactly 3 entries but was " + pathDetail.size());

        JsonNode from = pathDetail.get(0);
        JsonNode to = pathDetail.get(1);
        JsonNode val = pathDetail.get(2);

        PathDetail pd;
        if (val.isBoolean())
            pd = new PathDetail(val.asBoolean());
        else if (val.isLong())
            pd = new PathDetail(val.asLong());
        else if (val.isInt())
            pd = new PathDetail(val.asInt());
        else if (val.isDouble())
            pd = new PathDetail(val.asDouble());
        else if (val.isTextual())
            pd = new PathDetail(val.asText());
        else
            throw new JsonParseException(jp, "Unsupported type of PathDetail value " + pathDetail.getNodeType().name());

        pd.setFirst(from.asInt());
        pd.setLast(to.asInt());
        return pd;
    }
}
