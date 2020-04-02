package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.http.WebHelper;
import com.graphhopper.isochrone.algorithm.ContourBuilder;
import com.graphhopper.isochrone.algorithm.Isochrone;
import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.BlockAreaWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPoint;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.triangulate.ConformingDelaunayTriangulator;
import org.locationtech.jts.triangulate.ConstraintVertex;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.*;

@Path("isochrone")
public class IsochroneResource {

    private static final Logger logger = LoggerFactory.getLogger(IsochroneResource.class);

    private final GraphHopper graphHopper;
    private final EncodingManager encodingManager;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Inject
    public IsochroneResource(GraphHopper graphHopper, EncodingManager encodingManager) {
        this.graphHopper = graphHopper;
        this.encodingManager = encodingManager;
    }


    private Polygon heuristicallyFindMainConnectedComponent(MultiPolygon multiPolygon, Point point) {
        int maxPoints = 0;
        Polygon maxPolygon = null;
        for (int j = 0; j < multiPolygon.getNumGeometries(); j++) {
            Polygon polygon = (Polygon) multiPolygon.getGeometryN(j);
            if (polygon.contains(point)) {
                return polygon;
            }
            if (polygon.getNumPoints() > maxPoints) {
                maxPoints = polygon.getNumPoints();
                maxPolygon = polygon;
            }
        }
        return maxPolygon;
    }

}