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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Edge;
import com.graphhopper.util.EdgeList;

public class EdgeListSerializer extends JsonSerializer<EdgeList> {
    @Override
    public void serialize(EdgeList edges, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        List<Map<String, Object>> instrList = new ArrayList<>(edges.size());
        for (Edge edge : edges) {
            Map<String, Object> instrJson = new HashMap<>();
            instrList.add(instrJson);

            instrJson.put("street_name", edge.getName());
            instrJson.put("time", edge.getTime());
            instrJson.put("distance", Helper.round(edge.getDistance(), 3));
            instrJson.put("grade", edge.getGrade());
            instrJson.put("reversed", edge.getReversed());
            instrJson.put("penalty", edge.getPenalty());
            instrJson.put("time", edge.getTime());
            instrJson.put("weight", edge.getWeight());
            instrJson.put("points", edge.getPoints().toLineString(true));
            
        }
        jsonGenerator.writeObject(instrList);
    }
}
