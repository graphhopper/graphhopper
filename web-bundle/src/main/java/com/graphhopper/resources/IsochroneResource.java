package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.http.WebHelper;
import com.graphhopper.isochrone.algorithm.DelaunayTriangulationIsolineBuilder;
import com.graphhopper.isochrone.algorithm.Isochrone;
import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPoint;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

@Path("isochrone")
public class IsochroneResource {

    private static final Logger logger = LoggerFactory.getLogger(IsochroneResource.class);

    private final GraphHopper graphHopper;
    private final EncodingManager encodingManager;
    private final DelaunayTriangulationIsolineBuilder delaunayTriangulationIsolineBuilder;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Inject
    public IsochroneResource(GraphHopper graphHopper, EncodingManager encodingManager, DelaunayTriangulationIsolineBuilder delaunayTriangulationIsolineBuilder) {
        this.graphHopper = graphHopper;
        this.encodingManager = encodingManager;
        this.delaunayTriangulationIsolineBuilder = delaunayTriangulationIsolineBuilder;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response doGet(
            @Context HttpServletRequest httpReq,
            @Context UriInfo uriInfo,
            @QueryParam("vehicle") @DefaultValue("car") String vehicle,
            @QueryParam("buckets") @DefaultValue("1") int nBuckets,
            @QueryParam("reverse_flow") @DefaultValue("false") boolean reverseFlow,
            @QueryParam("point") GHPoint point,
            @QueryParam("result") @DefaultValue("polygon") String resultStr,
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
        QueryGraph queryGraph = new QueryGraph(graph);
        queryGraph.lookup(Collections.singletonList(qr));

        HintsMap hintsMap = new HintsMap();
        RouteResource.initHints(hintsMap, uriInfo.getQueryParameters());

        Weighting weighting = graphHopper.createWeighting(hintsMap, encoder, graph);
        Isochrone isochrone = new Isochrone(queryGraph, weighting, reverseFlow);

        if (distanceInMeter > 0) {
            isochrone.setDistanceLimit(distanceInMeter);
        } else {
            isochrone.setTimeLimit(timeLimitInSeconds);
        }

        if ("polygon".equalsIgnoreCase(resultStr)) {
            List<List<Coordinate>> buckets = isochrone.searchGPS(qr.getClosestNode(), nBuckets);
            if (isochrone.getVisitedNodes() > graphHopper.getMaxVisitedNodes() / 5) {
                throw new IllegalArgumentException("Server side reset: too many junction nodes would have to explored (" + isochrone.getVisitedNodes() + "). Let us know if you need this increased.");
            }

            int counter = 0;
            for (List<Coordinate> bucket : buckets) {
                if (bucket.size() < 2) {
                    throw new IllegalArgumentException("Too few points found for bucket " + counter + ". "
                            + "Please try a different 'point', a smaller 'buckets' count or a larger 'time_limit'. "
                            + "And let us know if you think this is a bug!");
                }
                counter++;
            }
            ArrayList<JsonFeature> features = new ArrayList<>();
            List<Coordinate[]> polygonShells = delaunayTriangulationIsolineBuilder.calcList(buckets, buckets.size() - 1);
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

        } else {
            throw new IllegalArgumentException("type not supported:" + resultStr);
        }
    }
}