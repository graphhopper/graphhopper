package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.http.GHPointParam;
import com.graphhopper.http.ProfileResolver;
import com.graphhopper.isochrone.algorithm.ContourBuilder;
import com.graphhopper.isochrone.algorithm.ShortestPathTree;
import com.graphhopper.isochrone.algorithm.Triangulator;
import com.graphhopper.jackson.ResponsePathSerializer;
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

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.HashMap;
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

    private final GraphHopper graphHopper;
    private final Triangulator triangulator;
    private final ProfileResolver profileResolver;
    private final String osmDate;

    @Inject
    public IsochroneResource(GraphHopper graphHopper, Triangulator triangulator, ProfileResolver profileResolver) {
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
            @QueryParam("full_geometry") @DefaultValue("false") boolean fullGeometry) {
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
            shortestPathTree.setWeightLimit(limit + Math.max(limit * 0.14, 200));
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

        Triangulator.Result result = triangulator.triangulate(snap, queryGraph, shortestPathTree, fz, degreesFromMeters(toleranceInMeter));

        ContourBuilder contourBuilder = new ContourBuilder(result.triangulation);
        ArrayList<Geometry> isochrones = new ArrayList<>();
        for (Double z : zs) {
            logger.info("Building contour z={}", z);
            MultiPolygon isochrone = contourBuilder.computeIsoline(z, result.seedEdges);
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
                properties.put("copyrights", ResponsePathSerializer.COPYRIGHTS);
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
            info.putPOJO("copyrights", ResponsePathSerializer.COPYRIGHTS);
            info.put("took", Math.round((float) sw.getMillis()));
            if (!osmDate.isEmpty()) info.put("road_data_timestamp", osmDate);
            finalJson = json;
        }

        logger.info("took: " + sw.getSeconds() + ", visited nodes:" + shortestPathTree.getVisitedNodes());
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
