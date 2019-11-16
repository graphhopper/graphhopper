package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.http.WebHelper;
import com.graphhopper.isochrone.algorithm.ContourBuilder;
import com.graphhopper.isochrone.algorithm.Isochrone;
import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

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

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response doGet(
            @Context UriInfo uriInfo,
            @QueryParam("vehicle") @DefaultValue("car") String vehicle,
            @QueryParam("buckets") @DefaultValue("1") int nBuckets,
            @QueryParam("reverse_flow") @DefaultValue("false") boolean reverseFlow,
            @QueryParam("point") GHPoint point,
            @QueryParam("time_limit") @DefaultValue("600") long timeLimitInSeconds,
            @QueryParam("distance_limit") @DefaultValue("-1") double distanceInMeter,
            @QueryParam("type") @DefaultValue("json") String respType) {

        if (nBuckets > 20 || nBuckets < 1)
            throw new IllegalArgumentException("Number of buckets has to be in the range [1, 20]");

        if (point == null)
            throw new IllegalArgumentException("point parameter cannot be null");

        StopWatch sw = new StopWatch().start();

        if (!encodingManager.hasEncoder(vehicle))
            throw new IllegalArgumentException("vehicle not supported:" + vehicle);

        if (respType != null && !respType.equalsIgnoreCase("json") && !respType.equalsIgnoreCase("geojson")) {
            throw new IllegalArgumentException("Format not supported:" + respType);
        }

        FlagEncoder encoder = encodingManager.getEncoder(vehicle);
        EdgeFilter edgeFilter = DefaultEdgeFilter.allEdges(encoder);
        LocationIndex locationIndex = graphHopper.getLocationIndex();
        QueryResult qr = locationIndex.findClosest(point.lat, point.lon, edgeFilter);
        if (!qr.isValid())
            throw new IllegalArgumentException("Point not found:" + point);

        Graph graph = graphHopper.getGraphHopperStorage();
        QueryGraph queryGraph = QueryGraph.lookup(graph, qr);

        HintsMap hintsMap = new HintsMap();
        RouteResource.initHints(hintsMap, uriInfo.getQueryParameters());

        Weighting weighting = graphHopper.createWeighting(hintsMap, encoder, graph);
        Isochrone isochrone = new Isochrone(queryGraph, weighting, reverseFlow);

        if (distanceInMeter > 0) {
            isochrone.setDistanceLimit(distanceInMeter);
        } else {
            isochrone.setTimeLimit(timeLimitInSeconds);
        }

        List<List<Coordinate>> buckets = isochrone.searchGPS(qr.getClosestNode(), nBuckets);
        if (isochrone.getVisitedNodes() > graphHopper.getMaxVisitedNodes() / 5) {
            throw new IllegalArgumentException("Too many nodes would have to explored (" + isochrone.getVisitedNodes() + "). Let us know if you need this increased.");
        }

        ArrayList<JsonFeature> features = new ArrayList<>();
        Collection<ConstraintVertex> sites = new ArrayList<>();
        for (int i = 0; i < buckets.size(); i++) {
            List<Coordinate> level = buckets.get(i);
            for (Coordinate coord : level) {
                ConstraintVertex site = new ConstraintVertex(coord);
                site.setZ(i);
                sites.add(site);
            }
        }

        ConformingDelaunayTriangulator conformingDelaunayTriangulator = new ConformingDelaunayTriangulator(sites, 0.0);
        conformingDelaunayTriangulator.setConstraints(new ArrayList(), new ArrayList());
        conformingDelaunayTriangulator.formInitialDelaunay();
        conformingDelaunayTriangulator.enforceConstraints();
        Geometry convexHull = conformingDelaunayTriangulator.getConvexHull();

        // If there's only one site (and presumably also if the convex hull is otherwise degenerated),
        // the triangulation only contains the frame, and not the site within the frame. Not sure if I agree with that.
        // See ConformingDelaunayTriangulator, it does include a buffer for the frame, but that buffer is zero
        // in these cases.
        // It leads to the following follow-up defect:
        // computeIsoline fails (returns an empty Multipolygon). This is clearly wrong, since
        // the idea is that every real (non-frame) vertex has positive-length-edges around it that can be traversed
        // to get a non-empty polygon.
        // So we exclude this case for now (it is indeed only a corner-case).

        if (!(convexHull instanceof Polygon)) {
            throw new IllegalArgumentException("Too few points found. "
                    + "Please try a different 'point' or a larger 'time_limit'.");
        }

        QuadEdgeSubdivision tin = conformingDelaunayTriangulator.getSubdivision();
        for (Vertex vertex : (Collection<Vertex>) tin.getVertices(true)) {
            if (tin.isFrameVertex(vertex)) {
                vertex.setZ(Double.MAX_VALUE);
            }
        }
        ArrayList<Coordinate[]> polygonShells = new ArrayList<>();
        ContourBuilder contourBuilder = new ContourBuilder(tin);
        for (int i = 0; i < buckets.size() - 1; i++) {
            MultiPolygon multiPolygon = contourBuilder.computeIsoline((double) i + 0.5);
            Polygon maxPolygon = heuristicallyFindMainConnectedComponent(multiPolygon, geometryFactory.createPoint(new Coordinate(point.lon, point.lat)));
            polygonShells.add(maxPolygon.getExteriorRing().getCoordinates());
        }
        for (Coordinate[] polygonShell : polygonShells) {
            JsonFeature feature = new JsonFeature();
            HashMap<String, Object> properties = new HashMap<>();
            properties.put("bucket", features.size());
            if (respType.equalsIgnoreCase("geojson")) {
                properties.put("copyrights", WebHelper.COPYRIGHTS);
            }
            feature.setProperties(properties);
            feature.setGeometry(geometryFactory.createPolygon(polygonShell));
            features.add(feature);
        }
        ObjectNode json = JsonNodeFactory.instance.objectNode();

        ObjectNode finalJson = null;
        if (respType.equalsIgnoreCase("geojson")) {
            json.put("type", "FeatureCollection");
            json.putPOJO("features", features);
            finalJson = json;
        } else {
            json.putPOJO("polygons", features);
            finalJson = WebHelper.jsonResponsePutInfo(json, sw.getSeconds());
        }

        sw.stop();
        logger.info("took: " + sw.getSeconds() + ", visited nodes:" + isochrone.getVisitedNodes() + ", " + uriInfo.getQueryParameters());
        return Response.ok(finalJson).header("X-GH-Took", "" + sw.getSeconds() * 1000).
                build();
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