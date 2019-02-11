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
package com.graphhopper.isochrone.algorithm;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.triangulate.ConformingDelaunayTriangulator;
import org.locationtech.jts.triangulate.ConstraintVertex;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Peter Karich
 * @author Michael Zilske
 */
public class DelaunayTriangulationIsolineBuilder {

    /**
     * @return a list of polygons wrapping the specified points
     */
    @SuppressWarnings("unchecked")
    public List<Coordinate[]> calcList(List<List<Coordinate>> pointLists, int maxIsolines) {

        if (maxIsolines > pointLists.size()) {
            throw new IllegalStateException("maxIsolines can only be smaller or equals to pointsList");
        }

        Collection<ConstraintVertex> sites = new ArrayList<>();
        for (int i = 0; i < pointLists.size(); i++) {
            List<Coordinate> level = pointLists.get(i);
            for (Coordinate coord : level) {
                ConstraintVertex site = new ConstraintVertex(coord);
                site.setZ((double) i);
                sites.add(site);
            }
        }
        ConformingDelaunayTriangulator conformingDelaunayTriangulator = new ConformingDelaunayTriangulator(sites, 0.0);
        conformingDelaunayTriangulator.setConstraints(new ArrayList(), new ArrayList());
        conformingDelaunayTriangulator.formInitialDelaunay();
        QuadEdgeSubdivision tin = conformingDelaunayTriangulator.getSubdivision();
        for (Vertex vertex : (Collection<Vertex>) tin.getVertices(true)) {
            if (tin.isFrameVertex(vertex)) {
                vertex.setZ(Double.MAX_VALUE);
            }
        }
        ArrayList<Coordinate[]> polygonShells = new ArrayList<>();
        ContourBuilder contourBuilder = new ContourBuilder(tin);
        // ignore the last isoline as it forms just the convex hull
        for (int i = 0; i < maxIsolines; i++) {
            MultiPolygon multiPolygon = contourBuilder.computeIsoline((double) i + 0.5);
            int maxPoints = 0;
            Polygon maxPolygon = null;
            for (int j = 0; j < multiPolygon.getNumGeometries(); j++) {
                Polygon polygon = (Polygon) multiPolygon.getGeometryN(j);
                if (polygon.getNumPoints() > maxPoints) {
                    maxPoints = polygon.getNumPoints();
                    maxPolygon = polygon;
                }
            }
            if (maxPolygon == null) {
                throw new IllegalStateException("no maximum polygon was found?");
            } else {
                polygonShells.add(maxPolygon.getExteriorRing().getCoordinates());
            }
        }
        return polygonShells;
    }

}
