package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.isochrone.algorithm.Isochrone;
import com.graphhopper.isochrone.algorithm.RasterHullBuilder;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.exceptions.GHException;
import com.graphhopper.util.shapes.GHPoint;
import com.vividsolutions.jts.geom.*;
import com.wdtinc.mapbox_vector_tile.VectorTile;
import com.wdtinc.mapbox_vector_tile.adapt.jts.IGeometryFilter;
import com.wdtinc.mapbox_vector_tile.adapt.jts.JtsAdapter;
import com.wdtinc.mapbox_vector_tile.adapt.jts.TileGeomResult;
import com.wdtinc.mapbox_vector_tile.adapt.jts.UserDataIgnoreConverter;
import com.wdtinc.mapbox_vector_tile.build.MvtLayerBuild;
import com.wdtinc.mapbox_vector_tile.build.MvtLayerParams;
import com.wdtinc.mapbox_vector_tile.build.MvtLayerProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

@Path("isochrone")
public class IsochroneResource {

    private static final Logger logger = LoggerFactory.getLogger(RouteResource.class);

    private final GraphHopper graphHopper;
    private final EncodingManager encodingManager;
    private final RasterHullBuilder rasterHullBuilder;

    @Inject
    public IsochroneResource(GraphHopper graphHopper, EncodingManager encodingManager, RasterHullBuilder rasterHullBuilder) {
        this.graphHopper = graphHopper;
        this.encodingManager = encodingManager;
        this.rasterHullBuilder = rasterHullBuilder;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response doGet(
            @Context HttpServletRequest httpReq,
            @Context UriInfo uriInfo,
            @QueryParam("vehicle") @DefaultValue("car") String vehicle,
            @QueryParam("weighting") @DefaultValue("fastest") String weightingStr,
            @QueryParam("buckets") @DefaultValue("1") int buckets,
            @QueryParam("reverse_flow") @DefaultValue("false") boolean reverseFlow,
            @QueryParam("point") GHPoint point,
            @QueryParam("result") @DefaultValue("edgelist-json") String resultStr,
            @QueryParam("time_limit") @DefaultValue("600") long timeLimitInSeconds,
            @QueryParam("distance_limit") @DefaultValue("-1") double distanceInMeter) {

        if (buckets > 20 || buckets < 1)
            throwArgExc("Number of buckets has to be in the range [1, 20]");

        if (point == null)
            throwArgExc("point parameter cannot be null");

        StopWatch sw = new StopWatch().start();

        if (!encodingManager.supports(vehicle))
            throwArgExc("vehicle not supported:" + vehicle);

        FlagEncoder encoder = encodingManager.getEncoder(vehicle);
        EdgeFilter edgeFilter = DefaultEdgeFilter.allEdges(encoder);
        LocationIndex locationIndex = graphHopper.getLocationIndex();
        QueryResult qr = locationIndex.findClosest(point.lat, point.lon, edgeFilter);
        if (!qr.isValid())
            throwArgExc("Point not found:" + point);

        Graph graph = graphHopper.getGraphHopperStorage();
        QueryGraph queryGraph = new QueryGraph(graph);
        queryGraph.lookup(Collections.singletonList(qr));

        HintsMap hintsMap = new HintsMap();
        RouteResource.initHints(hintsMap, uriInfo.getQueryParameters());

        Weighting weighting = graphHopper.createWeighting(hintsMap, encoder, graph);
        Isochrone isochrone = new Isochrone(queryGraph, weighting, reverseFlow);

        if (distanceInMeter > 0) {
            double maxMeter = 50 * 1000;
            if (distanceInMeter > maxMeter)
                throwArgExc("Specify a limit of less than " + maxMeter / 1000f + "km");
            if (buckets > (distanceInMeter / 500))
                throwArgExc("Specify buckets less than the number of explored kilometers");

            isochrone.setDistanceLimit(distanceInMeter);
        } else {

            long maxSeconds = 80 * 60;
            if (timeLimitInSeconds > maxSeconds)
                throwArgExc("Specify a limit of less than " + maxSeconds + " seconds");
            if (buckets > (timeLimitInSeconds / 60))
                throwArgExc("Specify buckets less than the number of explored minutes");

            isochrone.setTimeLimit(timeLimitInSeconds);
        }

        Object calcRes;

        if ("mvt".equalsIgnoreCase(resultStr)) {

            final GeometryFactory geomFactory = new GeometryFactory();
            // TODO increase to global boundaries - what's the purpose of this?
            final Envelope tileEnvelope = new Envelope(0d, 100, 0d, 100d);
            final MvtLayerParams DEFAULT_MVT_PARAMS = new MvtLayerParams();
            final IGeometryFilter ACCEPT_ALL_FILTER = geometry -> true;

            List<Number[]> edgeList = isochrone.searchEdges(qr.getClosestNode());
            LineString[] lineStrings = new LineString[edgeList.size()];
            for (int i = 0; i < edgeList.size(); i++) {
                final CoordinateSequence coordSeq = geomFactory.getCoordinateSequenceFactory().create(2, 2);
                Number[] edgeProps = edgeList.get(i);
                final Coordinate coord1 = coordSeq.getCoordinate(0);
                coord1.setOrdinate(0, edgeProps[0].doubleValue());
                coord1.setOrdinate(1, edgeProps[1].doubleValue());
                final Coordinate coord2 = coordSeq.getCoordinate(1);
                coord2.setOrdinate(0, edgeProps[2].doubleValue());
                coord2.setOrdinate(1, edgeProps[3].doubleValue());

                lineStrings[i] = new LineString(coordSeq, geomFactory);
            }
            final TileGeomResult tileGeom = JtsAdapter.createTileGeom(new MultiLineString(lineStrings, geomFactory), tileEnvelope, geomFactory,
                    DEFAULT_MVT_PARAMS, ACCEPT_ALL_FILTER);
            // "source-layer: "isochrone"
            final VectorTile.Tile mvt = encodeMvt("isochrone", DEFAULT_MVT_PARAMS, tileGeom);

            return Response.fromResponse(Response.ok(mvt.toByteArray(), new MediaType("application", "vnd.mapbox-vector-tile")).build())
                    .header("X-GH-Took", "" + sw.stop().getSeconds() * 1000)
                    .build();

        } else if ("edgelist-json".equalsIgnoreCase(resultStr)) {
            calcRes = isochrone.searchEdges(qr.getClosestNode());

        } else if ("edgelist".equalsIgnoreCase(resultStr)) {
            // write binary
            List<Number[]> edgeList = isochrone.searchEdges(qr.getClosestNode());

            // for every edge we store 6 floats
            int entrySizeInBytes = 6;
            // float size is 4
            ByteBuffer bb = ByteBuffer.allocate(2 * 4 + edgeList.size() * entrySizeInBytes * 4);
            bb.putInt(edgeList.size());
            bb.putInt(entrySizeInBytes * 4);
            for (int i = 0; i < edgeList.size(); i++) {
                Number[] entries = edgeList.get(i);
                for (int e = 0; e < entrySizeInBytes; e++) {
                    bb.putFloat(entries[e].floatValue());
                }
            }

            return Response.fromResponse(Response.ok(bb.array(), new MediaType("application", "octet-stream")).build())
                    .header("X-GH-Took", "" + sw.stop().getSeconds() * 1000)
                    .build();

        } else {

            List<List<Double[]>> list = isochrone.searchGPS(qr.getClosestNode(), buckets);
            if (isochrone.getVisitedNodes() > graphHopper.getMaxVisitedNodes() / 5) {
                throwArgExc("Server side reset: too many junction nodes would have to explored (" + isochrone.getVisitedNodes() + "). Let us know if you need this increased.");
            }

            int counter = 0;
            for (List<Double[]> tmp : list) {
                if (tmp.size() < 2) {
                    throwArgExc("Too few points found for bucket " + counter + ". "
                            + "Please try a different 'point', a smaller 'buckets' count or a larger 'time_limit'. "
                            + "And let us know if you think this is a bug!");
                }
                counter++;
            }

            if ("pointlist".equalsIgnoreCase(resultStr)) {
                calcRes = list;

            } else if ("polygon".equalsIgnoreCase(resultStr)) {
                list = rasterHullBuilder.calcList(list, list.size() - 1);

                ArrayList polyList = new ArrayList();
                int index = 0;
                for (List<Double[]> polygon : list) {
                    HashMap<String, Object> geoJsonMap = new HashMap<>();
                    HashMap<String, Object> propMap = new HashMap<>();
                    HashMap<String, Object> geometryMap = new HashMap<>();
                    polyList.add(geoJsonMap);
                    geoJsonMap.put("type", "Feature");
                    geoJsonMap.put("properties", propMap);
                    geoJsonMap.put("geometry", geometryMap);

                    propMap.put("bucket", index);
                    geometryMap.put("type", "Polygon");
                    // we have no holes => embed in yet another list
                    geometryMap.put("coordinates", Collections.singletonList(polygon));
                    index++;
                }
                calcRes = polyList;
            } else {
                throw new WebApplicationException(jsonErrorResponse(Collections.singletonList(new IllegalArgumentException("type not supported:" + resultStr))));
            }
        }

        logger.info("took: " + sw.getSeconds() + ", visited nodes:" + isochrone.getVisitedNodes() + ", " + uriInfo.getQueryParameters());
        return Response.fromResponse(jsonSuccessResponse(calcRes, sw.stop().getSeconds()))
                .header("X-GH-Took", "" + sw.stop().getSeconds() * 1000)
                .build();
    }

    private static VectorTile.Tile encodeMvt(String name, MvtLayerParams mvtParams, TileGeomResult tileGeom) {

        // Create MVT layer
        final VectorTile.Tile.Layer.Builder layerBuilder = MvtLayerBuild.newLayerBuilder(name, mvtParams);
        final MvtLayerProps layerProps = new MvtLayerProps();
        final UserDataIgnoreConverter ignoreUserData = new UserDataIgnoreConverter();

        // MVT tile geometry to MVT features
        final List<VectorTile.Tile.Feature> features = JtsAdapter.toFeatures(tileGeom.mvtGeoms, layerProps, ignoreUserData);
        layerBuilder.addAllFeatures(features);
        MvtLayerBuild.writeProps(layerBuilder, layerProps);

        // Build MVT layer
        final VectorTile.Tile.Layer layer = layerBuilder.build();

        final VectorTile.Tile.Builder tileBuilder = VectorTile.Tile.newBuilder();
        tileBuilder.addLayers(layer);
        return tileBuilder.build();
    }

    private void throwArgExc(String msg) {
        throw new WebApplicationException(jsonErrorResponse(Collections.singletonList(new IllegalArgumentException(msg))));
    }


    private Response jsonErrorResponse(List<Throwable> errors) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("message", getMessage(errors.get(0)));
        ArrayNode errorHintList = json.putArray("hints");
        for (Throwable t : errors) {
            ObjectNode error = errorHintList.addObject();
            error.put("message", getMessage(t));
            error.put("details", t.getClass().getName());
            if (t instanceof GHException) {
                ((GHException) t).getDetails().forEach(error::putPOJO);
            }
        }
        return Response.status(SC_BAD_REQUEST).entity(json).build();
    }

    private String getMessage(Throwable t) {
        if (t.getMessage() == null)
            return t.getClass().getSimpleName();
        else
            return t.getMessage();
    }

    private Response jsonSuccessResponse(Object result, float took) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.putPOJO("polygons", result);
        // If you replace GraphHopper with your own brand name, this is fine.
        // Still it would be highly appreciated if you mention us in your about page!
        final ObjectNode info = json.putObject("info");
        info.putArray("copyrights")
                .add("GraphHopper")
                .add("OpenStreetMap contributors");
        info.put("took", Math.round(took * 1000));

        return Response.ok(json).build();
    }
}
