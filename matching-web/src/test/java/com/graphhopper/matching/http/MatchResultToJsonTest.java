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

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.GPXExtension;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.routing.VirtualEdgeIteratorState;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GPXEntry;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint3D;

public class MatchResultToJsonTest {

    protected List<EdgeMatch> getEdgeMatch() {
        List<EdgeMatch> list = new ArrayList<EdgeMatch>();
        list.add(new EdgeMatch(getEdgeInterator(), getGpxExtension()));
        return list;
    }

    private List<GPXExtension> getGpxExtension() {
        List<GPXExtension> list = new ArrayList<GPXExtension>();
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

        list.add(new GPXExtension(new GPXEntry(-3.4446, -38.9996, 100000), queryResult1, 1));
        list.add(new GPXExtension(new GPXEntry(-3.4448, -38.9999, 100001), queryResult2, 1));
        return list;
    }

    private EdgeIteratorState getEdgeInterator() {
        PointList pointList = new PointList();
        pointList.add(-3.4445, -38.9990);
        pointList.add(-3.5550, -38.7990);
        VirtualEdgeIteratorState iterator = new VirtualEdgeIteratorState(0, 0, 0, 1, 10, 0, "test of iterator", pointList);
        return iterator;
    }

    @Test
    public void shouldCreateBasicStructure() {
        MatchResultToJson jsonResult = new MatchResultToJson(new MatchResult(getEdgeMatch()));
        JSONObject jsonObject = jsonResult.exportTo();

        Assert.assertTrue("root should have diary object", jsonObject.has("diary"));
        Assert.assertTrue("diary should be JSONObject", jsonObject.get("diary") instanceof JSONObject);
        Assert.assertTrue("diary should have entries a JSONArray", jsonObject.getJSONObject("diary").get("entries") instanceof JSONArray);
        Assert.assertTrue("Entry should br a JSONObject", jsonObject.getJSONObject("diary").getJSONArray("entries").get(0) instanceof JSONObject);

        JSONObject route = (JSONObject) jsonObject.getJSONObject("diary").getJSONArray("entries").get(0);

        Assert.assertTrue("route should have links array", route.get("links") instanceof JSONArray);

        JSONObject link = (JSONObject) route.getJSONArray("links").get(0);

        JSONObject geometry = new JSONObject(link.getString("geometry"));

        Assert.assertEquals("geometry should have type", "LineString", geometry.get("type"));
        Assert.assertEquals("geometry should have coordinates [[-38.999,-3.4445],[-38.799,-3.555]]", -38.999, geometry.getJSONArray("coordinates").getJSONArray(0).get(0));
        Assert.assertEquals("geometry should have coordinates [[-38.999,-3.4445],[-38.799,-3.555]]", -3.4445, geometry.getJSONArray("coordinates").getJSONArray(0).get(1));

        Assert.assertEquals("geometry should have coordinates [[-38.999,-3.4445],[-38.799,-3.555]]", -38.799, geometry.getJSONArray("coordinates").getJSONArray(1).get(0));
        Assert.assertEquals("geometry should have coordinates [[-38.999,-3.4445],[-38.799,-3.555]]", -3.555, geometry.getJSONArray("coordinates").getJSONArray(1).get(1));

        Assert.assertTrue("link should have wpts array", link.get("wpts") instanceof JSONArray);

        Assert.assertEquals("wpts[0].timestamp should exists", 100000l, link.getJSONArray("wpts").getJSONObject(0).get("timestamp"));
        Assert.assertEquals("wpts[0].y should exists", -3.4446, link.getJSONArray("wpts").getJSONObject(0).get("y"));
        Assert.assertEquals("wpts[0].x should exists", -38.9996, link.getJSONArray("wpts").getJSONObject(0).get("x"));

        Assert.assertEquals("wpts[1].timestamp should exists", 100001l, link.getJSONArray("wpts").getJSONObject(1).get("timestamp"));
        Assert.assertEquals("wpts[1].y should exists", -3.4449, link.getJSONArray("wpts").getJSONObject(1).get("y"));
        Assert.assertEquals("wpts[1].x should exists", -38.9999, link.getJSONArray("wpts").getJSONObject(1).get("x"));

    }
}
