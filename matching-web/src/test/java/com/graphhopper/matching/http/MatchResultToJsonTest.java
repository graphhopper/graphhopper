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
import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.GPXExtension;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.routing.VirtualEdgeIteratorState;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GPXEntry;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint3D;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MatchResultToJsonTest {

    private List<EdgeMatch> getEdgeMatch() {
        List<EdgeMatch> list = new ArrayList<>();
        list.add(new EdgeMatch(getEdgeInterator(), getGpxExtension()));
        return list;
    }

    private List<GPXExtension> getGpxExtension() {
        List<GPXExtension> list = new ArrayList<>();
        QueryResult queryResult1 = new QueryResult(-3.4445, -38.9990) {
            @Override
            public GHPoint3D getSnappedPoint() {
                return new GHPoint3D(-3.4446, -38.9996, 0);
            }
        };
        QueryResult queryResult2 = new QueryResult(-3.4445, -38.9990) {
            @Override
            public GHPoint3D getSnappedPoint() {
                return new GHPoint3D(-3.4449, -38.9999, 0);
            }
        };

        list.add(new GPXExtension(new GPXEntry(-3.4446, -38.9996, 100000), queryResult1));
        list.add(new GPXExtension(new GPXEntry(-3.4448, -38.9999, 100001), queryResult2));
        return list;
    }

    private EdgeIteratorState getEdgeInterator() {
        PointList pointList = new PointList();
        pointList.add(-3.4445, -38.9990);
        pointList.add(-3.5550, -38.7990);
        return new VirtualEdgeIteratorState(0, 0, 0, 1, 10, 0, "test of iterator", pointList);
    }

    @Test
    public void shouldCreateBasicStructure() throws JsonProcessingException {
        final ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonObject = MatchResultToJson.convertToTree(new MatchResult(getEdgeMatch()), objectMapper);
        ;
        jsonObject = objectMapper.convertValue(jsonObject, JsonNode.class);
        JsonNode route = jsonObject.get("diary").get("entries").get(0);
        JsonNode link = route.get("links").get(0);
        JsonNode geometry = link.get("geometry");
        Assert.assertEquals("geometry should have type", "LineString", geometry.get("type").asText());
        Assert.assertEquals("geometry should have coordinates", "[[-38.999,-3.4445],[-38.799,-3.555]]", objectMapper.writeValueAsString(geometry.get("coordinates")));

        Assert.assertEquals("wpts[0].timestamp should exists", 100000l, link.get("wpts").get(0).get("timestamp").asLong());
        Assert.assertEquals("wpts[0].y should exists", "-3.4446", link.get("wpts").get(0).get("y").asText());
        Assert.assertEquals("wpts[0].x should exists", "-38.9996", link.get("wpts").get(0).get("x").asText());

        Assert.assertEquals("wpts[1].timestamp should exists", 100001l, link.get("wpts").get(1).get("timestamp").asLong());
        Assert.assertEquals("wpts[1].y should exists", "-3.4449", link.get("wpts").get(1).get("y").asText());
        Assert.assertEquals("wpts[1].x should exists", "-38.9999", link.get("wpts").get(1).get("x").asText());
    }
}
