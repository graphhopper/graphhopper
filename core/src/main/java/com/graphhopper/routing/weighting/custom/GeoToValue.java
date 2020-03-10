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
package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.Polygon;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;

final class GeoToValue implements ConfigMapEntry {
    public static String key(String postfix) {
        return "area_" + postfix;
    }

    private final Polygon ghPolygon;
    private final double value, elseValue;
    private NodeAccess nodeAccess;

    public GeoToValue(Graph graph, PreparedGeometry geometry, double value, double elseValue) {
        if (graph == null)
            throw new IllegalArgumentException("graph cannot be null. Required for GeoToValue");
        this.nodeAccess = graph.getNodeAccess();
        this.ghPolygon = new Polygon(geometry);
        this.value = value;
        this.elseValue = elseValue;
    }

    public void setQueryGraph(QueryGraph queryGraph) {
        // the initial nodeAccess is from baseGraph sufficient for LocationIndex; for routing we need QueryGraph:
        this.nodeAccess = queryGraph.getNodeAccess();
    }

    static Geometry pickGeometry(CustomModel customModel, String key) {
        String id = key.substring(GeoToValue.key("").length());
        JsonFeature feature = customModel.getAreas().get(id);
        if (feature == null)
            throw new IllegalArgumentException("Cannot find area " + id);
        return feature.getGeometry();
    }

    @Override
    public double getValue(EdgeIteratorState edgeState, boolean reverse) {
        BBox bbox = GHUtility.createBBox(nodeAccess, edgeState);
        if (ghPolygon.getBounds().intersects(bbox)) {
            PointList pointList = edgeState.fetchWayGeometry(3).makeImmutable();
            if (ghPolygon.intersects(pointList))
                return value;
        }
        return elseValue;
    }

    @Override
    public String toString() {
        return ghPolygon.toString() + ": " + value + ", " + elseValue;
    }
}
