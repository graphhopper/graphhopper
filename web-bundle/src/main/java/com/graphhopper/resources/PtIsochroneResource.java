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

package com.graphhopper.resources;

import com.graphhopper.isochrone.algorithm.ContourBuilder;
import com.graphhopper.reader.gtfs.*;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateList;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.triangulate.ConformingDelaunayTriangulator;
import com.vividsolutions.jts.triangulate.ConstraintVertex;
import com.vividsolutions.jts.triangulate.DelaunayTriangulationBuilder;
import com.vividsolutions.jts.triangulate.quadedge.QuadEdgeSubdivision;
import com.vividsolutions.jts.triangulate.quadedge.Vertex;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

@Path("isochrone")
public class PtIsochroneResource {

    private static final double JTS_TOLERANCE = 0.00001;

    private GtfsStorage gtfsStorage;
    private EncodingManager encodingManager;
    private GraphHopperStorage graphHopperStorage;
    private LocationIndex locationIndex;

    private final Function<Label, Double> z = label -> (double) label.currentTime;

    public PtIsochroneResource(GtfsStorage gtfsStorage, EncodingManager encodingManager, GraphHopperStorage graphHopperStorage, LocationIndex locationIndex) {
        this.gtfsStorage = gtfsStorage;
        this.encodingManager = encodingManager;
        this.graphHopperStorage = graphHopperStorage;
        this.locationIndex = locationIndex;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Geometry doGet(
            @QueryParam("point") GHPoint source,
            @QueryParam("time_limit") @DefaultValue("600") long seconds,
            @QueryParam("reverse_flow") @DefaultValue("false") boolean reverseFlow,
            @QueryParam(Parameters.PT.EARLIEST_DEPARTURE_TIME) String departureTimeString) {
        Instant initialTime;
        try {
            initialTime = Instant.parse(departureTimeString);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "Illegal value for required parameter %s: [%s]", Parameters.PT.EARLIEST_DEPARTURE_TIME, departureTimeString));
        }

        double targetZ = initialTime.toEpochMilli() + seconds * 1000;

        GeometryFactory geometryFactory = new GeometryFactory();
        QueryGraph queryGraph = new QueryGraph(graphHopperStorage);
        final EdgeFilter filter = DefaultEdgeFilter.allEdges(graphHopperStorage.getEncodingManager().getEncoder("foot"));
        QueryResult queryResult = locationIndex.findClosest(source.lat, source.lon, filter);
        queryGraph.lookup(Collections.singletonList(queryResult));
        if (!queryResult.isValid()) {
            throw new IllegalArgumentException("Cannot find point: " + source);
        }

        PtFlagEncoder ptFlagEncoder = (PtFlagEncoder) encodingManager.getEncoder("pt");
        GraphExplorer graphExplorer = new GraphExplorer(queryGraph, new FastestWeighting(encodingManager.getEncoder("foot")), ptFlagEncoder, gtfsStorage, RealtimeFeed.empty(gtfsStorage), reverseFlow, Collections.emptyList(), false, 5.0);
        MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, ptFlagEncoder, reverseFlow, Double.MAX_VALUE, false, false, false, 1000000);

        Map<Coordinate, Double> z1 = new HashMap<>();
        NodeAccess nodeAccess = queryGraph.getNodeAccess();

        MultiCriteriaLabelSetting.SPTVisitor sptVisitor = nodeLabel -> {
            Coordinate nodeCoordinate = new Coordinate(nodeAccess.getLongitude(nodeLabel.node), nodeAccess.getLatitude(nodeLabel.node));
            z1.merge(nodeCoordinate, this.z.apply(nodeLabel), Math::min);
        };
        router.calcLabelsAndNeighbors(queryResult.getClosestNode(), -1, initialTime, 0, sptVisitor, label -> label.currentTime <= targetZ);

        CoordinateList siteCoords = DelaunayTriangulationBuilder.extractUniqueCoordinates(geometryFactory.createMultiPoint(z1.keySet().toArray(new Coordinate[0])));


        List<ConstraintVertex> constraintVertices = new ArrayList();
        for (Object siteCoord : siteCoords) {
            Coordinate coord = (Coordinate) siteCoord;
            constraintVertices.add(new ConstraintVertex(coord));
        }

        ConformingDelaunayTriangulator cdt = new ConformingDelaunayTriangulator(constraintVertices, JTS_TOLERANCE);
        cdt.setConstraints(new ArrayList(), new ArrayList());
        cdt.formInitialDelaunay();

        QuadEdgeSubdivision tin = cdt.getSubdivision();

        for (Vertex vertex : (Collection<Vertex>) tin.getVertices(true)) {
            if (tin.isFrameVertex(vertex)) {
                vertex.setZ(Double.MAX_VALUE);
            } else {
                Double aDouble = z1.get(vertex.getCoordinate());
                if (aDouble != null) {
                    vertex.setZ(aDouble);
                } else {
                    vertex.setZ(Double.MAX_VALUE);
                }
            }
        }

        ContourBuilder contourBuilder = new ContourBuilder(tin);
        return contourBuilder.computeIsoline(targetZ);
    }

}
