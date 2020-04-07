package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.ProfileConfig;
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

import static com.graphhopper.util.Parameters.Routing.EDGE_BASED;
import static com.graphhopper.util.Parameters.Routing.TURN_COSTS;

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

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response doGet(
            @Context UriInfo uriInfo,
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

        if (respType != null && !respType.equalsIgnoreCase("json") && !respType.equalsIgnoreCase("geojson"))
            throw new IllegalArgumentException("Format not supported:" + respType);

        HintsMap hintsMap = new HintsMap();
        RouteResource.initHints(hintsMap, uriInfo.getQueryParameters());
        if (!hintsMap.getBool(Parameters.CH.DISABLE, true))
            throw new IllegalArgumentException("Currently you cannot use speed mode for /isochrone, Do not use `ch.disable=false`");
        if (!hintsMap.getBool(Parameters.Landmark.DISABLE, true))
            throw new IllegalArgumentException("Currently you cannot use hybrid mode for /isochrone, Do not use `lm.disable=false`");
        if (hintsMap.getBool(Parameters.Routing.EDGE_BASED, false))
            throw new IllegalArgumentException("Currently you cannot use edge-based for /isochrone. Do not use `edge_based=true`");
        if (hintsMap.getBool(TURN_COSTS, false))
            throw new IllegalArgumentException("Currently you cannot use turn costs for /isochrone, Do not use `turn_costs=true`");

        hintsMap.putObject(Parameters.CH.DISABLE, true);
        hintsMap.putObject(Parameters.Landmark.DISABLE, true);
        // ignore these parameters for profile selection, because we fall back to node-based without turn costs so far
        hintsMap.remove(TURN_COSTS);
        hintsMap.remove(EDGE_BASED);
        // todo: #1934, only try to resolve the profile if no profile is given!
        ProfileConfig profile = profileResolver.resolveProfile(hintsMap);
        FlagEncoder encoder = encodingManager.getEncoder(profile.getVehicle());
        EdgeFilter edgeFilter = DefaultEdgeFilter.allEdges(encoder);
        LocationIndex locationIndex = graphHopper.getLocationIndex();
        QueryResult qr = locationIndex.findClosest(point.lat, point.lon, edgeFilter);
        if (!qr.isValid())
            throw new IllegalArgumentException("Point not found:" + point);

        Graph graph = graphHopper.getGraphHopperStorage();
        QueryGraph queryGraph = QueryGraph.lookup(graph, qr);

        // have to disable turn costs, as isochrones are running node-based
        Weighting weighting = graphHopper.createWeighting(profile, hintsMap, true);
        if (hintsMap.has(Parameters.Routing.BLOCK_AREA))
            weighting = new BlockAreaWeighting(weighting, GraphEdgeIdFinder.createBlockArea(graph, locationIndex,
                    Collections.singletonList(point), hintsMap, DefaultEdgeFilter.allEdges(encoder)));
        ShortestPathTree shortestPathTree = new ShortestPathTree(queryGraph, weighting, reverseFlow);

        double limit;
        if (distanceInMeter > 0) {
            limit = distanceInMeter;
            shortestPathTree.setDistanceLimit(limit + Math.max(limit * 0.14, 2_000));
        } else {
            limit = timeLimitInSeconds * 1000;
            shortestPathTree.setTimeLimit(limit + Math.max(limit * 0.14, 200_000));
        }
        ArrayList<Double> zs = new ArrayList<>();
        for (int i = 0; i < nBuckets; i++) {
            zs.add(limit / (nBuckets - i));
        }

        final NodeAccess na = queryGraph.getNodeAccess();
        Collection<ConstraintVertex> sites = new ArrayList<>();
        shortestPathTree.search(qr.getClosestNode(), label -> {
            double exploreValue;
            if (distanceInMeter > 0) {
                exploreValue = label.distance;
            } else {
                exploreValue = label.time;
            }
            double lat = na.getLatitude(label.adjNode);
            double lon = na.getLongitude(label.adjNode);
            ConstraintVertex site = new ConstraintVertex(new Coordinate(lon, lat));
            site.setZ(exploreValue);
            sites.add(site);

            // guess center of road to increase precision a bit for longer roads
            if (label.parent != null) {
                double lat2 = na.getLatitude(label.parent.adjNode);
                double lon2 = na.getLongitude(label.parent.adjNode);
                ConstraintVertex site2 = new ConstraintVertex(new Coordinate((lon + lon2) / 2, (lat + lat2) / 2));
                site2.setZ(exploreValue);
                sites.add(site2);
            }
        });
        if (shortestPathTree.getVisitedNodes() > graphHopper.getMaxVisitedNodes() / 5) {
            throw new IllegalArgumentException("Too many nodes would have to explored (" + shortestPathTree.getVisitedNodes() + "). Let us know if you need this increased.");
        }

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
        logger.info("took: " + sw.getSeconds() + ", visited nodes:" + shortestPathTree.getVisitedNodes() + ", " + uriInfo.getQueryParameters());
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