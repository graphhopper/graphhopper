package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.http.GHPointParam;
import com.graphhopper.http.WebHelper;
import com.graphhopper.isochrone.algorithm.ContourBuilder;
import com.graphhopper.isochrone.algorithm.ShortestPathTree;
import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.routing.ProfileResolver;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.BlockAreaWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.StopWatch;
import io.dropwizard.jersey.params.IntParam;
import io.dropwizard.jersey.params.LongParam;
import org.hibernate.validator.constraints.Range;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.triangulate.ConformingDelaunayTriangulator;
import org.locationtech.jts.triangulate.ConstraintVertex;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

import static com.graphhopper.resources.IsochroneResource.ResponseType.geojson;
import static com.graphhopper.resources.RouteResource.errorIfLegacyParameters;
import static com.graphhopper.resources.RouteResource.removeLegacyParameters;
import static com.graphhopper.routing.util.TraversalMode.EDGE_BASED;
import static com.graphhopper.routing.util.TraversalMode.NODE_BASED;

@Path("isochrone")
public class IsochroneResource {

    private static final Logger logger = LoggerFactory.getLogger(IsochroneResource.class);

    private final GraphHopper graphHopper;
    private final ProfileResolver profileResolver;
    private final EncodingManager encodingManager;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Inject
    public IsochroneResource(GraphHopper graphHopper, ProfileResolver profileResolver, EncodingManager encodingManager) {
        this.graphHopper = graphHopper;
        this.profileResolver = profileResolver;
        this.encodingManager = encodingManager;
    }

    enum ResponseType {json, geojson}

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGet(
            @Context UriInfo uriInfo,
            @QueryParam("profile") String profileName,
            @QueryParam("buckets") @Range(min = 1, max = 20) @DefaultValue("1") IntParam nBuckets,
            @QueryParam("reverse_flow") @DefaultValue("false") boolean reverseFlow,
            @QueryParam("point") @NotNull GHPointParam point,
            @QueryParam("time_limit") @DefaultValue("600") LongParam timeLimitInSeconds,
            @QueryParam("distance_limit") @DefaultValue("-1") LongParam distanceLimitInMeter,
            @QueryParam("weight_limit") @DefaultValue("-1") LongParam weightLimit,
            @QueryParam("type") @DefaultValue("json") ResponseType respType) {
        StopWatch sw = new StopWatch().start();

        PMap hintsMap = new PMap();
        RouteResource.initHints(hintsMap, uriInfo.getQueryParameters());
        hintsMap.putObject(Parameters.CH.DISABLE, true);
        hintsMap.putObject(Parameters.Landmark.DISABLE, true);
        if (Helper.isEmpty(profileName)) {
            profileName = profileResolver.resolveProfile(hintsMap).getName();
            removeLegacyParameters(hintsMap);
        }
        errorIfLegacyParameters(hintsMap);

        Profile profile = graphHopper.getProfile(profileName);
        if (profile == null) {
            throw new IllegalArgumentException("The requested profile '" + profileName + "' does not exist");
        }
        FlagEncoder encoder = encodingManager.getEncoder(profile.getVehicle());
        EdgeFilter edgeFilter = DefaultEdgeFilter.allEdges(encoder);
        LocationIndex locationIndex = graphHopper.getLocationIndex();
        QueryResult qr = locationIndex.findClosest(point.get().lat, point.get().lon, edgeFilter);
        if (!qr.isValid())
            throw new IllegalArgumentException("Point not found:" + point);

        Graph graph = graphHopper.getGraphHopperStorage();
        QueryGraph queryGraph = QueryGraph.create(graph, qr);

        Weighting weighting = graphHopper.createWeighting(profile, hintsMap);
        if (hintsMap.has(Parameters.Routing.BLOCK_AREA))
            weighting = new BlockAreaWeighting(weighting, GraphEdgeIdFinder.createBlockArea(graph, locationIndex,
                    Collections.singletonList(point.get()), hintsMap, DefaultEdgeFilter.allEdges(encoder)));
        TraversalMode traversalMode = profile.isTurnCosts() ? EDGE_BASED : NODE_BASED;
        ShortestPathTree shortestPathTree = new ShortestPathTree(queryGraph, weighting, reverseFlow, traversalMode);

        double limit;
        if (weightLimit.get() > 0) {
            limit = weightLimit.get();
            shortestPathTree.setWeightLimit(limit + Math.max(limit * 0.14, 2_000));
        } else if (distanceLimitInMeter.get() > 0) {
            limit = distanceLimitInMeter.get();
            shortestPathTree.setDistanceLimit(limit + Math.max(limit * 0.14, 2_000));
        } else {
            limit = timeLimitInSeconds.get() * 1000;
            shortestPathTree.setTimeLimit(limit + Math.max(limit * 0.14, 200_000));
        }
        ArrayList<Double> zs = new ArrayList<>();
        for (int i = 0; i < nBuckets.get(); i++) {
            zs.add(limit / (nBuckets.get() - i));
        }

        final NodeAccess na = queryGraph.getNodeAccess();
        Collection<ConstraintVertex> sites = new ArrayList<>();
        shortestPathTree.search(qr.getClosestNode(), label -> {
            double exploreValue;
            if (weightLimit.get() > 0) {
                exploreValue = label.weight;
            } else if (distanceLimitInMeter.get() > 0) {
                exploreValue = label.distance;
            } else {
                exploreValue = label.time;
            }
            double lat = na.getLatitude(label.node);
            double lon = na.getLongitude(label.node);
            ConstraintVertex site = new ConstraintVertex(new Coordinate(lon, lat));
            site.setZ(exploreValue);
            sites.add(site);

            // guess center of road to increase precision a bit for longer roads
            if (label.parent != null) {
                double lat2 = na.getLatitude(label.parent.node);
                double lon2 = na.getLongitude(label.parent.node);
                ConstraintVertex site2 = new ConstraintVertex(new Coordinate((lon + lon2) / 2, (lat + lat2) / 2));
                site2.setZ(exploreValue);
                sites.add(site2);
            }
        });
        int consumedNodes = sites.size();
        if (consumedNodes > graphHopper.getMaxVisitedNodes() / 3)
            throw new IllegalArgumentException("Too many nodes would be included in post processing (" + consumedNodes + "). Let us know if you need this increased.");

        // Sites may contain repeated coordinates. Especially for edge-based traversal, that's expected -- we visit
        // each node multiple times.
        // But that's okay, the triangulator de-dupes by itself, and it keeps the first z-value it sees, which is
        // what we want.

        ArrayList<JsonFeature> features = new ArrayList<>();
        ConformingDelaunayTriangulator conformingDelaunayTriangulator = new ConformingDelaunayTriangulator(sites, 0.0);
        conformingDelaunayTriangulator.setConstraints(new ArrayList<>(), new ArrayList<>());
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
        ContourBuilder contourBuilder = new ContourBuilder(tin.getEdges());

        for (Double z : zs) {
            MultiPolygon multiPolygon = contourBuilder.computeIsoline(z);
            Polygon maxPolygon = heuristicallyFindMainConnectedComponent(multiPolygon, geometryFactory.createPoint(new Coordinate(point.get().lon, point.get().lat)));
            polygonShells.add(maxPolygon.getExteriorRing().getCoordinates());
        }
        for (Coordinate[] polygonShell : polygonShells) {
            JsonFeature feature = new JsonFeature();
            HashMap<String, Object> properties = new HashMap<>();
            properties.put("bucket", features.size());
            if (respType == geojson) {
                properties.put("copyrights", WebHelper.COPYRIGHTS);
            }
            feature.setProperties(properties);
            feature.setGeometry(geometryFactory.createPolygon(polygonShell));
            features.add(feature);
        }
        ObjectNode json = JsonNodeFactory.instance.objectNode();

        ObjectNode finalJson = null;
        if (respType == geojson) {
            json.put("type", "FeatureCollection");
            json.putPOJO("features", features);
            finalJson = json;
        } else {
            json.putPOJO("polygons", features);
            finalJson = WebHelper.jsonResponsePutInfo(json, sw.getMillis());
        }

        sw.stop();
        logger.info("took: " + sw.getSeconds() + ", visited nodes:" + shortestPathTree.getVisitedNodes()
                + ", consumed nodes:" + consumedNodes + ", " + uriInfo.getQueryParameters());
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