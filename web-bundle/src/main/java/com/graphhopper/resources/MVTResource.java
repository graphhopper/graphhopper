package com.graphhopper.resources;

import com.graphhopper.GraphHopper;
import com.graphhopper.isochrone.algorithm.DelaunayTriangulationIsolineBuilder;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.BBox;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Path("mvt")
public class MVTResource {

    private static final Logger logger = LoggerFactory.getLogger(MVTResource.class);

    private final GraphHopper graphHopper;
    private final EncodingManager encodingManager;
    private final DelaunayTriangulationIsolineBuilder delaunayTriangulationIsolineBuilder;

    @Inject
    public MVTResource(GraphHopper graphHopper, EncodingManager encodingManager, DelaunayTriangulationIsolineBuilder delaunayTriangulationIsolineBuilder) {
        this.graphHopper = graphHopper;
        this.encodingManager = encodingManager;
        this.delaunayTriangulationIsolineBuilder = delaunayTriangulationIsolineBuilder;
    }

    @GET
    @Path("{z}/{x}/{y}.mvt")
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGetXyz(
            @Context HttpServletRequest httpReq,
            @Context UriInfo uriInfo,
            @QueryParam("vehicle") @DefaultValue("car") String vehicle,
            @PathParam("z") int z,
            @PathParam("x") int x,
            @PathParam("y") int y) {
        return doGet(httpReq, uriInfo, vehicle, x, y, z);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response doGet(
            @Context HttpServletRequest httpReq,
            @Context UriInfo uriInfo,
            @QueryParam("vehicle") @DefaultValue("car") String vehicle,
            @QueryParam("x") int xInfo,
            @QueryParam("y") int yInfo,
            @QueryParam("z") int zInfo) {

        if (zInfo <= 9) {
            VectorTile.Tile.Builder mvtBuilder = VectorTile.Tile.newBuilder();
            return Response.fromResponse(Response.ok(mvtBuilder.build().toByteArray(), new MediaType("application", "x-protobuf")).build())
                    .header("X-GH-Took", "0")
                    .build();
        }

        StopWatch totalSW = new StopWatch().start();
        Coordinate nw = num2deg(xInfo, yInfo, zInfo);
        Coordinate se = num2deg(xInfo + 1, yInfo + 1, zInfo);
        LocationIndexTree locationIndex = (LocationIndexTree) graphHopper.getLocationIndex();
        final NodeAccess na = graphHopper.getGraphHopperStorage().getNodeAccess();
        EdgeExplorer edgeExplorer = graphHopper.getGraphHopperStorage().createEdgeExplorer(DefaultEdgeFilter.ALL_EDGES);
        BBox bbox = new BBox(nw.x, se.x, se.y, nw.y);
        if (!bbox.isValid())
            throw new IllegalStateException("Invalid bbox " + bbox);

        final GeometryFactory geometryFactory = new GeometryFactory();
        VectorTile.Tile.Builder mvtBuilder = VectorTile.Tile.newBuilder();
        final IGeometryFilter acceptAllGeomFilter = geometry -> true;
        final Envelope tileEnvelope = new Envelope(se, nw);
        final MvtLayerParams layerParams = new MvtLayerParams(256, 4096);
        final UserDataKeyValueMapConverter converter = new UserDataKeyValueMapConverter();
        final DecimalEncodedValue averageSpeedEnc = encodingManager.getDecimalEncodedValue(EncodingManager.getKey(encodingManager.getEncoder(vehicle), "average_speed"));
        final AtomicInteger edgeCounter = new AtomicInteger(0);
        // in toFeatures addTags of the converter is called and layerProps is filled with keys&values => those need to be stored in the layerBuilder
        // otherwise the decoding won't be successful and "undefined":"undefined" instead of "speed": 30 is the result
        final MvtLayerProps layerProps = new MvtLayerProps();
        final VectorTile.Tile.Layer.Builder layerBuilder = MvtLayerBuild.newLayerBuilder("roads", layerParams);
        locationIndex.query(bbox, new LocationIndexTree.EdgeVisitor(edgeExplorer) {
            @Override
            public void onEdge(EdgeIteratorState edge, int nodeA, int nodeB) {
                LineString lineString;
                int intSpeed = (int) Math.round(edge.get(averageSpeedEnc));
                if (zInfo >= 12) {
                    PointList pl = edge.fetchWayGeometry(3);
                    lineString = pl.toLineString(false);
                } else if (intSpeed > 80 || zInfo == 10 && intSpeed >= 50 || zInfo == 11 && intSpeed >= 30 || zInfo > 11) {
                    double lat = na.getLatitude(nodeA);
                    double lon = na.getLongitude(nodeA);
                    double toLat = na.getLatitude(nodeB);
                    double toLon = na.getLongitude(nodeB);
                    lineString = geometryFactory.createLineString(new Coordinate[]{new Coordinate(lon, lat), new Coordinate(toLon, toLat)});
                } else {
                    // skip edge for certain zoom
                    return;
                }

                edgeCounter.incrementAndGet();
                Map<String, Object> map = new HashMap<>(2);
                map.put("speed", intSpeed);
                map.put("name", edge.getName());
                lineString.setUserData(map);

                // doing some AffineTransformation
                TileGeomResult tileGeom = JtsAdapter.createTileGeom(lineString, tileEnvelope, geometryFactory, layerParams, acceptAllGeomFilter);
                List<VectorTile.Tile.Feature> features = JtsAdapter.toFeatures(tileGeom.mvtGeoms, layerProps, converter);
                layerBuilder.addAllFeatures(features);
            }

            @Override
            public void onCellBBox(BBox bbox, int width) {
            }
        });

        MvtLayerBuild.writeProps(layerBuilder, layerProps);
        mvtBuilder.addLayers(layerBuilder.build());
        byte[] bytes = mvtBuilder.build().toByteArray();
        totalSW.stop();
        logger.info("took: " + totalSW.getSeconds() + ", edges:" + edgeCounter.get());
        return Response.fromResponse(Response.ok(bytes, new MediaType("application", "x-protobuf")).build())
                .header("X-GH-Took", "" + totalSW.getSeconds() * 1000)
                .build();
    }

    Coordinate num2deg(int xInfo, int yInfo, int zoom) {
        double n = Math.pow(2, zoom);
        double lonDeg = xInfo / n * 360.0 - 180.0;
        // unfortunately latitude numbers goes from north to south
        double latRad = Math.atan(Math.sinh(Math.PI * (1 - 2 * yInfo / n)));
        double latDeg = Math.toDegrees(latRad);
        return new Coordinate(lonDeg, latDeg);
    }
}
