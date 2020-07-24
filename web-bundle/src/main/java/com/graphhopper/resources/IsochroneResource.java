package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.http.GHPointParam;
import com.graphhopper.http.WebHelper;
import com.graphhopper.isochrone.algorithm.ContourBuilder;
import com.graphhopper.isochrone.algorithm.ReadableTriangulation;
import com.graphhopper.isochrone.algorithm.ShortestPathTree;
import com.graphhopper.isochrone.algorithm.Triangulator;
import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.routing.ProfileResolver;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.BlockAreaWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.StopWatch;
import io.dropwizard.jersey.params.IntParam;
import io.dropwizard.jersey.params.LongParam;
import org.hibernate.validator.constraints.Range;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
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

        Collection<Coordinate> sites = shortestPathTree.searchSites(qr.getClosestNode());
        int consumedNodes = sites.size();
        if (consumedNodes > graphHopper.getRouterConfig().getMaxVisitedNodes() / 3)
            throw new IllegalArgumentException("Too many nodes would be included in post processing (" + consumedNodes + "). Let us know if you need this increased.");

        // Sites may contain repeated coordinates. Especially for edge-based traversal, that's expected -- we visit
        // each node multiple times.
        // But that's okay, the triangulator de-dupes by itself, and it keeps the first z-value it sees, which is
        // what we want.

        ReadableTriangulation triangulation = Triangulator.getTriangulation(sites);
        ContourBuilder contourBuilder = new ContourBuilder(triangulation);
        ArrayList<Coordinate[]> polygonShells = new ArrayList<>();
        for (Double z : zs) {
            MultiPolygon multiPolygon = contourBuilder.computeIsoline(z);
            Polygon maxPolygon = ContourBuilder.heuristicallyFindMainConnectedComponent(multiPolygon, geometryFactory.createPoint(new Coordinate(point.get().lon, point.get().lat)));
            polygonShells.add(maxPolygon.getExteriorRing().getCoordinates());
        }
        ArrayList<JsonFeature> features = new ArrayList<>();
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

}