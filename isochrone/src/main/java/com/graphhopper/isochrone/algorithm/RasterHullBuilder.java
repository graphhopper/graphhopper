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

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.triangulate.ConformingDelaunayTriangulator;
import com.vividsolutions.jts.triangulate.ConstraintVertex;
import com.vividsolutions.jts.triangulate.quadedge.QuadEdgeSubdivision;
import com.vividsolutions.jts.triangulate.quadedge.Vertex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Peter Karich
 * @author Michael Zilske
 */
public class RasterHullBuilder {

    /**
     * @return a list of polygons wrapping the specified points
     */
    @SuppressWarnings("unchecked")
    public List<List<Double[]>> calcList(List<List<Double[]>> pointsList, int maxIsolines) {

        if (maxIsolines > pointsList.size()) {
            throw new IllegalStateException("maxIsolines can only be smaller or equals to pointsList");
        }

        Collection<ConstraintVertex> sites = new ArrayList<>();
        for (int i = 0; i < pointsList.size(); i++) {
            List<Double[]> level = pointsList.get(i);
            for (Double[] xy : level) {
                ConstraintVertex site = new ConstraintVertex(new Coordinate(xy[0], xy[1]));
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
        ArrayList<List<Double[]>> polygons = new ArrayList<>();
        ContourBuilder contourBuilder = new ContourBuilder(tin);
        // ignore the last polygon as it forms just the convex hull
        for (int i = 0; i < maxIsolines; i++) {
            Geometry geometry = contourBuilder.computeIsoline((double) i + 0.5);
            List<Double[]> coords = new ArrayList<Double[]>();
            if (geometry instanceof MultiPolygon) {
                MultiPolygon mPoly = (MultiPolygon) geometry;
                int maxPoints = 0;
                Geometry maxGeo = null;
                for (int j = 0; j < mPoly.getNumGeometries(); j++) {
                    Geometry geo = mPoly.getGeometryN(j);
                    if (geo.getNumPoints() > maxPoints) {
                        maxPoints = geo.getNumPoints();
                        maxGeo = geo;
                    }
                }

                if (maxGeo == null) {
                    throw new IllegalStateException("no maximum polygon was found?");
                } else {
                    fillExteriorRing(coords, maxGeo);
                }
            } else if (geometry instanceof Polygon) {
                fillExteriorRing(coords, geometry);
            } else {
                throw new IllegalStateException("geometry no (multi)polygon");
            }

            polygons.add(coords);
        }

        return polygons;
    }

    private void fillExteriorRing(List<Double[]> coords, Geometry geo) {
        if (geo instanceof Polygon) {
            // normally this will be picked
            Polygon poly = (Polygon) geo;
            LineString ls = poly.getExteriorRing();
            for (int j = 0; j < ls.getNumPoints(); j++) {
                Point p = ls.getPointN(j);
                coords.add(new Double[]{p.getX(), p.getY()});
            }
        } else {
            int len = geo.getCoordinates().length;
            Coordinate first = geo.getCoordinates()[0];
            for (int j = 0; j < len; j++) {
                Coordinate coord = geo.getCoordinates()[j];
                // lon, lat
                coords.add(new Double[]{coord.x, coord.y});

                if (j > 10 && coord.x == first.x && coord.y == first.y) {
                    break;
                }
            }
        }
    }

}
