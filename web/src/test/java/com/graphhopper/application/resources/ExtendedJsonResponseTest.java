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

package com.graphhopper.application.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.Observation;
import com.graphhopper.matching.State;
import com.graphhopper.resources.MapMatchingResource;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExtendedJsonResponseTest {

    @Test
    public void shouldCreateBasicStructure() {
        JsonNode jsonObject = MapMatchingResource.convertToTree(new MatchResult(getEdgeMatch()), false, false);
        JsonNode route = jsonObject.get("diary").get("entries").get(0);
        JsonNode link = route.get("links").get(0);
        JsonNode geometry = link.get("geometry");
        assertEquals("LineString", geometry.get("type").asText(), "geometry should have type");
        assertEquals("LINESTRING (-38.999 -3.4445, -38.799 -3.555)", geometry.get("coordinates").asText(), "geometry should have coordinates");

        assertEquals("-3.4446", link.get("wpts").get(0).get("y").asText(), "wpts[0].y should exists");
        assertEquals("-38.9996", link.get("wpts").get(0).get("x").asText(), "wpts[0].x should exists");

        assertEquals("-3.4449", link.get("wpts").get(1).get("y").asText(), "wpts[1].y should exists");
        assertEquals("-38.9999", link.get("wpts").get(1).get("x").asText(), "wpts[1].x should exists");
    }

    private List<EdgeMatch> getEdgeMatch() {
        List<EdgeMatch> list = new ArrayList<>();
        list.add(new EdgeMatch(getEdgeIterator(), getGpxExtension()));
        return list;
    }

    private List<State> getGpxExtension() {
        List<State> list = new ArrayList<>();
        Snap snap1 = new Snap(-3.4445, -38.9990) {
            @Override
            public GHPoint3D getSnappedPoint() {
                return new GHPoint3D(-3.4446, -38.9996, 0);
            }
        };
        Snap snap2 = new Snap(-3.4445, -38.9990) {
            @Override
            public GHPoint3D getSnappedPoint() {
                return new GHPoint3D(-3.4449, -38.9999, 0);
            }
        };

        list.add(new State(new Observation(new GHPoint(-3.4446, -38.9996)), snap1));
        list.add(new State(new Observation(new GHPoint(-3.4448, -38.9999)), snap2));
        return list;
    }

    private EdgeIteratorState getEdgeIterator() {
        PointList pointList = new PointList();
        pointList.add(-3.4445, -38.9990);
        pointList.add(-3.5550, -38.7990);
        return new VirtualEdgeIteratorState(0, 0, 0, 1, 10, new IntsRef(1),
                KVStorage.KeyValue.createKV(KVStorage.KeyValue.STREET_NAME, "test of iterator"), pointList, false);
    }

}
