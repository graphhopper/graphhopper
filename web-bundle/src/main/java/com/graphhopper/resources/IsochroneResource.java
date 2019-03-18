package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.isochrone.algorithm.DelaunayTriangulationIsolineBuilder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import com.wdtinc.mapbox_vector_tile.VectorTile;
import com.wdtinc.mapbox_vector_tile.adapt.jts.IGeometryFilter;
import com.wdtinc.mapbox_vector_tile.adapt.jts.JtsAdapter;
import com.wdtinc.mapbox_vector_tile.adapt.jts.TileGeomResult;
import com.wdtinc.mapbox_vector_tile.adapt.jts.UserDataKeyValueMapConverter;
import com.wdtinc.mapbox_vector_tile.build.MvtLayerBuild;
import com.wdtinc.mapbox_vector_tile.build.MvtLayerParams;
import com.wdtinc.mapbox_vector_tile.build.MvtLayerProps;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Path("isochrone")
public class IsochroneResource {

    private static final Logger logger = LoggerFactory.getLogger(RouteResource.class);

    private final GraphHopper graphHopper;
    private final EncodingManager encodingManager;
    private final DelaunayTriangulationIsolineBuilder delaunayTriangulationIsolineBuilder;
    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final ConcurrentHashMap<Integer, List<Number[]>> map = new ConcurrentHashMap<>();

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
            @QueryParam("x") int xInfo,
            @QueryParam("y") int yInfo,
            @QueryParam("z") int zInfo,
            @QueryParam("time_limit") @DefaultValue("600") long timeLimitInSeconds,
            @QueryParam("distance_limit") @DefaultValue("-1") double distanceInMeter) {

        if (nBuckets > 20 || nBuckets < 1)
            throw new IllegalArgumentException("Number of buckets has to be in the range [1, 20]");

        if (point == null)
            throw new IllegalArgumentException("point parameter cannot be null");

        StopWatch sw = new StopWatch().start();

        if (!encodingManager.hasEncoder(vehicle))
            throw new IllegalArgumentException("vehicle not supported:" + vehicle);

        Coordinate nw = num2deg(xInfo, yInfo, zInfo);
        Coordinate se = num2deg(xInfo + 1, yInfo + 1, zInfo);

        if ("mvt".equalsIgnoreCase(resultStr) && zInfo > 10 && (nw.y < 49 && nw.y > 48.3) && (nw.x > 10.5 && nw.x < 11.5)) {
            LocationIndexTree locationIndex = (LocationIndexTree) graphHopper.getLocationIndex();
            final NodeAccess na = graphHopper.getGraphHopperStorage().getNodeAccess();
            final List<Double[]> edgeList = new ArrayList<>(100);
            EdgeExplorer edgeExplorer = graphHopper.getGraphHopperStorage().createEdgeExplorer(DefaultEdgeFilter.ALL_EDGES);
            BBox bbox = new BBox(nw.x, se.x, se.y, nw.y);
            if (!bbox.isValid())
                throw new IllegalStateException("Invalid bbox " + bbox);

            locationIndex.query(bbox, new LocationIndexTree.EdgeVisitor(edgeExplorer) {
                @Override
                public void onEdge(int edgeId, int nodeA, int nodeB) {
                    double lat = na.getLatitude(nodeA);
                    double lon = na.getLongitude(nodeA);
                    double toLat = na.getLatitude(nodeB);
                    double toLon = na.getLongitude(nodeB);
                    edgeList.add(new Double[]{lon, lat, toLon, toLat});
                }

                @Override
                public void onCellBBox(BBox bbox, int width) {
                }
            });

            IGeometryFilter acceptAllGeomFilter = geometry -> true;
            Envelope tileEnvelope = new Envelope(se, nw);

            MvtLayerParams layerParams = new MvtLayerParams(256, 4096);
            GeometryFactory geometryFactory = new GeometryFactory();

            LineString[] edgeListGeometry = new LineString[edgeList.size()];
            for (int edgeCounter = 0; edgeCounter < edgeList.size(); edgeCounter++) {
                Double[] edge = edgeList.get(edgeCounter);
                edgeListGeometry[edgeCounter] = geometryFactory.createLineString(
                        new Coordinate[]{new Coordinate(edge[0], edge[1]), new Coordinate(edge[2], edge[3])});
            }

            logger.info("start encoding: " + resultStr + ", edges:" + 1);
            // doing some AffineTransformation
            TileGeomResult tileGeom = JtsAdapter.createTileGeom(
                    geometryFactory.createMultiLineString(edgeListGeometry),
                    tileEnvelope,
                    geometryFactory,
                    layerParams,
                    acceptAllGeomFilter);

            MvtLayerProps layerProps = new MvtLayerProps();
            VectorTile.Tile.Layer.Builder layerBuilder = MvtLayerBuild.newLayerBuilder("roads", layerParams);
            MvtLayerBuild.writeProps(layerBuilder, layerProps);
            List<VectorTile.Tile.Feature> features = JtsAdapter.toFeatures(tileGeom.mvtGeoms, layerProps, new UserDataKeyValueMapConverter());
            VectorTile.Tile mvt = VectorTile.Tile.newBuilder().addLayers(layerBuilder.addAllFeatures(features).build()).build();
            byte[] bytes = mvt.toByteArray();
            String tookStr = "" + sw.stop().getSeconds() * 1000;
            logger.info("took: " + tookStr);
            return Response.fromResponse(Response.ok(bytes, new MediaType("application", "x-protobuf")).build())
                    .header("X-GH-Took", tookStr)
                    .build();

        }
//        else if ("pointlist".equalsIgnoreCase(resultStr)) {
//            sw.stop();
//            logger.info("took: " + sw.getSeconds() + ", visited nodes:" + isochrone.getVisitedNodes() + ", " + uriInfo.getQueryParameters());
//            return Response.fromResponse(jsonSuccessResponse(buckets, sw.getSeconds()))
//                    .header("X-GH-Took", "" + sw.getSeconds() * 1000)
//                    .build();
//        } else if ("polygon".equalsIgnoreCase(resultStr)) {
//            ArrayList<JsonFeature> features = new ArrayList<>();
//            List<Coordinate[]> polygonShells = delaunayTriangulationIsolineBuilder.calcList(buckets, buckets.size() - 1);
//            for (Coordinate[] polygonShell : polygonShells) {
//                JsonFeature feature = new JsonFeature();
//                HashMap<String, Object> properties = new HashMap<>();
//                properties.put("bucket", features.size());
//                feature.setProperties(properties);
//                feature.setGeometry(geometryFactory.createPolygon(polygonShell));
//                features.add(feature);
//            }
//            sw.stop();
//            logger.info("took: " + sw.getSeconds() + ", visited nodes:" + isochrone.getVisitedNodes() + ", " + uriInfo.getQueryParameters());
//            return Response.fromResponse(jsonSuccessResponse(features, sw.getSeconds()))
//                    .header("X-GH-Took", "" + sw.getSeconds() * 1000)
//                    .build();
//        }
        else {
            throw new IllegalArgumentException("type not supported:" + resultStr);
        }
    }

    Coordinate num2deg(int xInfo, int yInfo, int zoom) {
        double n = Math.pow(2, zoom);
        double lonDeg = xInfo / n * 360.0 - 180.0;
        // unfortunately latitude numbers goes from north to south
        double latRad = Math.atan(Math.sinh(Math.PI * (1 - 2 * yInfo / n)));
        double latDeg = Math.toDegrees(latRad);
        return new Coordinate(lonDeg, latDeg);
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
