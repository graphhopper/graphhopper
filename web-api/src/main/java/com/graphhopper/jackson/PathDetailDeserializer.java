/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.jackson;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.core.util.details.PathDetail;

import java.io.IOException;
import java.util.Map;

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
        else if (val.isDouble())
            pd = new PathDetail(val.asDouble());
        else if (val.canConvertToLong())
            pd = new PathDetail(val.asLong());
        else if (val.isTextual())
            pd = new PathDetail(val.asText());
        else if (val.isObject())
            pd = new PathDetail(jp.getCodec().treeToValue(val, Map.class));
        else if (val.isNull())
            pd = new PathDetail(null);
        else
            throw new JsonParseException(jp, "Unsupported type of PathDetail value " + pathDetail.getNodeType().name());

        pd.setFirst(from.asInt());
        pd.setLast(to.asInt());
        return pd;
    }
}
