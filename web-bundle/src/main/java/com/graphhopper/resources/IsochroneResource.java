package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.http.GHPointParam;
import com.graphhopper.isochrone.algorithm.ContourBuilder;
import com.graphhopper.isochrone.algorithm.ShortestPathTree;
import com.graphhopper.isochrone.algorithm.Triangulator;
import com.graphhopper.jackson.ResponsePathSerializer;
import com.graphhopper.routing.ProfileResolver;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.FiniteWeightFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.BlockAreaWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import io.dropwizard.jersey.params.IntParam;
import io.dropwizard.jersey.params.LongParam;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.function.ToDoubleFunction;

import static com.graphhopper.resources.IsochroneResource.ResponseType.geojson;
import static com.graphhopper.resources.RouteResource.errorIfLegacyParameters;
import static com.graphhopper.resources.RouteResource.removeLegacyParameters;
import static com.graphhopper.routing.util.TraversalMode.EDGE_BASED;
import static com.graphhopper.routing.util.TraversalMode.NODE_BASED;

@Path("isochrone")
public class IsochroneResource {

    private static final Logger logger = LoggerFactory.getLogger(IsochroneResource.class);

    private final GraphHopper graphHopper;
    private final Triangulator triangulator;
    private final ProfileResolver profileResolver;

    @Inject
    public IsochroneResource(GraphHopper graphHopper, Triangulator triangulator, ProfileResolver profileResolver) {
        this.graphHopper = graphHopper;
        this.triangulator = triangulator;
        this.profileResolver = profileResolver;
    }

    public enum ResponseType {json, geojson}

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
            @QueryParam("type") @DefaultValue("json") ResponseType respType,
            @QueryParam("tolerance") @DefaultValue("0") double toleranceInMeter,
            @QueryParam("full_geometry") @DefaultValue("false") boolean fullGeometry) {
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
        if (profile == null)
            throw new IllegalArgumentException("The requested profile '" + profileName + "' does not exist");
        LocationIndex locationIndex = graphHopper.getLocationIndex();
        Graph graph = graphHopper.getGraphHopperStorage();
        Weighting weighting = graphHopper.createWeighting(profile, hintsMap);
        BooleanEncodedValue inSubnetworkEnc = graphHopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profileName));
        if (hintsMap.has(Parameters.Routing.BLOCK_AREA)) {
            GraphEdgeIdFinder.BlockArea blockArea = GraphEdgeIdFinder.createBlockArea(graph, locationIndex,
                    Collections.singletonList(point.get()), hintsMap, new FiniteWeightFilter(weighting));
            weighting = new BlockAreaWeighting(weighting, blockArea);
        }
        Snap snap = locationIndex.findClosest(point.get().lat, point.get().lon, new DefaultSnapFilter(weighting, inSubnetworkEnc));
        if (!snap.isValid())
            throw new IllegalArgumentException("Point not found:" + point);
        QueryGraph queryGraph = QueryGraph.create(graph, snap);
        TraversalMode traversalMode = profile.isTurnCosts() ? EDGE_BASED : NODE_BASED;
        ShortestPathTree shortestPathTree = new ShortestPathTree(queryGraph, queryGraph.wrapWeighting(weighting), reverseFlow, traversalMode);

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
        double delta = limit / nBuckets.get();
        for (int i = 0; i < nBuckets.get(); i++) {
            zs.add((i + 1) * delta);
        }

        ToDoubleFunction<ShortestPathTree.IsoLabel> fz;
        if (weightLimit.get() > 0) {
            fz = l -> l.weight;
        } else if (distanceLimitInMeter.get() > 0) {
            fz = l -> l.distance;
        } else {
            fz = l -> l.time;
        }

        Triangulator.Result result = triangulator.triangulate(snap, queryGraph, shortestPathTree, fz, degreesFromMeters(toleranceInMeter));

        ContourBuilder contourBuilder = new ContourBuilder(result.triangulation);
        ArrayList<Geometry> isochrones = new ArrayList<>();
        for (Double z : zs) {
            logger.info("Building contour z={}", z);
            MultiPolygon isochrone = contourBuilder.computeIsoline(z, result.seedEdges);
            if (!isochrone.isEmpty()) {
                if (fullGeometry) {
                    isochrones.add(isochrone);
                } else {
                    Polygon maxPolygon = heuristicallyFindMainConnectedComponent(isochrone, isochrone.getFactory().createPoint(new Coordinate(point.get().lon, point.get().lat)));
                    isochrones.add(isochrone.getFactory().createPolygon(((LinearRing) maxPolygon.getExteriorRing())));
                }
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