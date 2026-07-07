package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.http.GHPointParam;
import com.graphhopper.http.ProfileResolver;
import com.graphhopper.isochrone.algorithm.ContourBuilder;
import com.graphhopper.isochrone.algorithm.ShortestPathTree;
import com.graphhopper.isochrone.algorithm.Triangulator;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import org.hibernate.validator.constraints.Range;
import org.locationtech.jts.geom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.ToDoubleFunction;

import static com.graphhopper.resources.IsochroneResource.ResponseType.geojson;
import static com.graphhopper.resources.RouteResource.removeLegacyParameters;
import static com.graphhopper.routing.util.TraversalMode.EDGE_BASED;
import static com.graphhopper.routing.util.TraversalMode.NODE_BASED;

@Path("isochrone")
public class IsochroneResource {

    private static final Logger logger = LoggerFactory.getLogger(IsochroneResource.class);

    private final GraphHopperConfig config;
    private final GraphHopper graphHopper;
    private final Triangulator triangulator;
    private final ProfileResolver profileResolver;
    private final String osmDate;

    @Inject
    public IsochroneResource(GraphHopperConfig config, GraphHopper graphHopper, Triangulator triangulator, ProfileResolver profileResolver) {
        this.config = config;
        this.graphHopper = graphHopper;
        this.triangulator = triangulator;
        this.profileResolver = profileResolver;
        this.osmDate = graphHopper.getProperties().get("datareader.data.date");
    }

    public enum ResponseType {json, geojson}

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGet(
            @Context UriInfo uriInfo,
            @QueryParam("profile") String profileName,
            @QueryParam("buckets") @Range(min = 1, max = 20) @DefaultValue("1") OptionalInt nBuckets,
            @QueryParam("reverse_flow") @DefaultValue("false") boolean reverseFlow,
            @QueryParam("point") @NotNull GHPointParam point,
            @QueryParam("time_limit") @DefaultValue("600") OptionalLong timeLimitInSeconds,
            @QueryParam("distance_limit") @DefaultValue("-1") OptionalLong distanceLimitInMeter,
            @QueryParam("weight_limit") @DefaultValue("-1") OptionalLong weightLimit,
            @QueryParam("type") @DefaultValue("json") ResponseType respType,
            @QueryParam("tolerance") @DefaultValue("0") double toleranceInMeter,
            @QueryParam("full_geometry") @DefaultValue("false") boolean fullGeometry,
            @QueryParam("algorithm") @DefaultValue("tinfour") String algorithm) {
        StopWatch sw = new StopWatch().start();
        PMap hintsMap = new PMap();
        RouteResource.initHints(hintsMap, uriInfo.getQueryParameters());
        hintsMap.putObject(Parameters.CH.DISABLE, true);
        hintsMap.putObject(Parameters.Landmark.DISABLE, true);

        PMap profileResolverHints = new PMap(hintsMap);
        profileResolverHints.putObject("profile", profileName);
        profileName = profileResolver.resolveProfile(profileResolverHints);
        removeLegacyParameters(hintsMap);

        Profile profile = graphHopper.getProfile(profileName);
        if (profile == null)
            throw new IllegalArgumentException("The requested profile '" + profileName + "' does not exist");
        LocationIndex locationIndex = graphHopper.getLocationIndex();
        BaseGraph graph = graphHopper.getBaseGraph();
        Weighting weighting = graphHopper.createWeighting(profile, hintsMap);
        BooleanEncodedValue inSubnetworkEnc = graphHopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profileName));
        Snap snap = locationIndex.findClosest(point.get().lat, point.get().lon, new DefaultSnapFilter(weighting, inSubnetworkEnc));
        if (!snap.isValid())
            throw new IllegalArgumentException("Point not found:" + point);
        QueryGraph queryGraph = QueryGraph.create(graph, snap);
        TraversalMode traversalMode = profile.hasTurnCosts() ? EDGE_BASED : NODE_BASED;
        ShortestPathTree shortestPathTree = new ShortestPathTree(queryGraph, queryGraph.wrapWeighting(weighting), reverseFlow, traversalMode);

        double limit;
        ToDoubleFunction<ShortestPathTree.IsoLabel> fz;
        if (weightLimit.orElseThrow(() -> new IllegalArgumentException("query param weight_limit is not a number.")) > 0) {
            limit = weightLimit.getAsLong();
            shortestPathTree.setWeightLimit(limit + Math.max(limit * 0.14, 2000));
            fz = l -> l.weight;
        } else if (distanceLimitInMeter.orElseThrow(() -> new IllegalArgumentException("query param distance_limit is not a number.")) > 0) {
            limit = distanceLimitInMeter.getAsLong();
            shortestPathTree.setDistanceLimit(limit + Math.max(limit * 0.14, 2_000));
            fz = l -> l.distance;
        } else {
            limit = timeLimitInSeconds.orElseThrow(() -> new IllegalArgumentException("query param time_limit is not a number.")) * 1000d;
            shortestPathTree.setTimeLimit(limit + Math.max(limit * 0.14, 200_000));
            fz = l -> l.time;
        }
        ArrayList<Double> zs = new ArrayList<>();
        double delta = limit / nBuckets.orElseThrow(() -> new IllegalArgumentException("query param buckets is not a number."));
        for (int i = 0; i < nBuckets.getAsInt(); i++) {
            zs.add((i + 1) * delta);
        }

        // one MultiPolygon per bucket, built either via the JTS Delaunay+ContourBuilder pipeline
        // or via the Tinfour library (algorithm=tinfour). Both consume the same shortest-path-tree.
        ObjectNode debug = JsonNodeFactory.instance.objectNode();
        List<Geometry> rawIsolines;
        if ("tinfour".equalsIgnoreCase(algorithm)) {
            boolean semiVirtual = true;
            // according to javadocs SemiVirtualIncrementalTin is 30% slower per insert
            // but in practise is nearly as fast as normal one but uses a lot less memory and waste.
            TinfourIsochroneBuilder builder = new TinfourIsochroneBuilder(semiVirtual);
            rawIsolines = builder.computeIsolines(snap, queryGraph, shortestPathTree, fz, zs);
            debug.put("sites", builder.vertexCount);
            debug.put("search_ms", builder.searchMillis);
            debug.put("sort_ms", builder.sortMillis);
            // same key as the JTS branch so the two engines can be compared directly (Tinfour additionally
            // breaks out search_ms/sort_ms, which the JTS triangulate_ms bundles in)
            debug.put("triangulate_ms", builder.tinBuildMillis);
            debug.put("contour_ms", builder.contourMillis);
        } else {
            StopWatch swTriangulate = new StopWatch().start();
            Triangulator.Result result = triangulator.triangulate(snap, queryGraph, shortestPathTree, fz, degreesFromMeters(toleranceInMeter));
            debug.put("triangulate_ms", swTriangulate.stop().getMillis());
            ContourBuilder contourBuilder = new ContourBuilder(result.triangulation);
            rawIsolines = new ArrayList<>(zs.size());
            StopWatch swContour = new StopWatch().start();
            for (Double z : zs) {
                logger.info("Building contour z={}", z);
                rawIsolines.add(contourBuilder.computeIsoline(z, result.seedEdges));
            }
            debug.put("contour_ms", swContour.stop().getMillis());
        }
        debug.put("algorithm", algorithm);
        debug.put("visited_nodes", shortestPathTree.getVisitedNodes());

        ArrayList<Geometry> isochrones = new ArrayList<>();
        for (Geometry rawIsoline : rawIsolines) {
            MultiPolygon isochrone = (MultiPolygon) rawIsoline;
            if (fullGeometry) {
                isochrones.add(isochrone);
            } else {
                Polygon maxPolygon = heuristicallyFindMainConnectedComponent(isochrone, isochrone.getFactory().createPoint(new Coordinate(point.get().lon, point.get().lat)));
                isochrones.add(isochrone.getFactory().createPolygon(((LinearRing) maxPolygon.getExteriorRing())));
            }
        }
        ArrayList<JsonFeature> features = new ArrayList<>();
        for (Geometry isochrone : isochrones) {
            JsonFeature feature = new JsonFeature();
            HashMap<String, Object> properties = new HashMap<>();
            properties.put("bucket", features.size());
            if (respType == geojson) {
                properties.put("copyrights", config.getCopyrights());
            }
            feature.setProperties(properties);
            feature.setGeometry(isochrone);
            features.add(feature);
        }
        ObjectNode json = JsonNodeFactory.instance.objectNode();

        sw.stop();
        ObjectNode finalJson = null;
        if (respType == geojson) {
            json.put("type", "FeatureCollection");
            json.putPOJO("features", features);
            finalJson = json;
        } else {
            json.putPOJO("polygons", features);
            final ObjectNode info = json.putObject("info");
            info.putPOJO("copyrights", config.getCopyrights());
            info.put("took", Math.round((float) sw.getMillis()));
            info.set("debug", debug);
            if (!osmDate.isEmpty()) info.put("road_data_timestamp", osmDate);
            finalJson = json;
        }

        logger.info("took: " + sw.getSeconds() + ", visited nodes:" + shortestPathTree.getVisitedNodes() + ", debug:" + debug);
        // expose the timing breakdown as headers too, so it is visible for any response type (e.g. geojson)
        Response.ResponseBuilder responseBuilder = Response.ok(finalJson).header("X-GH-Took", "" + sw.getSeconds() * 1000);
        debug.fields().forEachRemaining(e -> responseBuilder.header("X-GH-Iso-" + e.getKey(), e.getValue().asText()));
        return responseBuilder.build();
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

    /**
     * We want to specify a tolerance in something like meters, but we need it in unprojected lat/lon-space.
     * This is more correct in some parts of the world, and in some directions, than in others.
     *
     * @param distanceInMeters distance in meters
     * @return "distance" in degrees
     */
    static double degreesFromMeters(double distanceInMeters) {
        return distanceInMeters / DistanceCalcEarth.METERS_PER_DEGREE;
    }

}
