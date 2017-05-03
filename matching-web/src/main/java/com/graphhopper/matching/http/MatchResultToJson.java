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
package com.graphhopper.matching.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.GPXExtension;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.util.PointList;

class MatchResultToJson {

    static JsonNode convertToTree(MatchResult result, ObjectMapper objectMapper) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode diary = root.putObject("diary");
        ArrayNode entries = diary.putArray("entries");
        ObjectNode route = entries.addObject();
        ArrayNode links = route.putArray("links");
        for (int emIndex = 0; emIndex < result.getEdgeMatches().size(); emIndex++) {
            ObjectNode link = links.addObject();
            EdgeMatch edgeMatch = result.getEdgeMatches().get(emIndex);
            PointList pointList = edgeMatch.getEdgeState().fetchWayGeometry(emIndex == 0 ? 3 : 2);
            final ObjectNode geometry = link.putObject("geometry");
            if (pointList.size() < 2) {
                geometry.putArray("coordinates").add(objectMapper.convertValue(pointList.toGeoJson().get(0), JsonNode.class));
                geometry.put("type", "Point");
            } else {
                geometry.putArray("coordinates").addAll(objectMapper.convertValue(pointList.toGeoJson(), ArrayNode.class));
                geometry.put("type", "LineString");
            }
            link.put("id", edgeMatch.getEdgeState().getEdge());
            ArrayNode wpts = link.putArray("wpts");
            for (GPXExtension extension : edgeMatch.getGpxExtensions()) {
                ObjectNode wpt = wpts.addObject();
                wpt.put("x", extension.getQueryResult().getSnappedPoint().lon);
                wpt.put("y", extension.getQueryResult().getSnappedPoint().lat);
                wpt.put("timestamp", extension.getEntry().getTime());
            }
        }
        try {
            System.out.println(objectMapper.writeValueAsString(root));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return root;
    }
}
