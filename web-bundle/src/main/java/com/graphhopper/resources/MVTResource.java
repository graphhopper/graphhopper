package com.graphhopper.resources;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.*;
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
    private static final MediaType PBF = new MediaType("application", "x-protobuf");
    private final GraphHopper graphHopper;
    private final EncodingManager encodingManager;

    @Inject
    public MVTResource(GraphHopper graphHopper, EncodingManager encodingManager) {
        this.graphHopper = graphHopper;
        this.encodingManager = encodingManager;
    }

    @GET
    @Path("{z}/{x}/{y}.mvt")
    @Produces("application/x-protobuf")
    public Response doGetXyz(
            @Context HttpServletRequest httpReq,
            @Context UriInfo uriInfo,
            @PathParam("z") int zInfo,
            @PathParam("x") int xInfo,
            @PathParam("y") int yInfo,
            @QueryParam(Parameters.Details.PATH_DETAILS) List<String> pathDetails) {

        if (zInfo <= 9) {
            VectorTile.Tile.Builder mvtBuilder = VectorTile.Tile.newBuilder();
            return Response.fromResponse(Response.ok(mvtBuilder.build().toByteArray(), PBF).build())
                    .header("X-GH-Took", "0")
                    .build();
        }

        StopWatch totalSW = new StopWatch().start();
        Coordinate nw = num2deg(xInfo, yInfo, zInfo);
        Coordinate se = num2deg(xInfo + 1, yInfo + 1, zInfo);
        LocationIndexTree locationIndex = (LocationIndexTree) graphHopper.getLocationIndex();
        final NodeAccess na = graphHopper.getBaseGraph().getNodeAccess();
        BBox bbox = new BBox(nw.x, se.x, se.y, nw.y);
        if (!bbox.isValid())
            throw new IllegalStateException("Invalid bbox " + bbox);

        final GeometryFactory geometryFactory = new GeometryFactory();
        VectorTile.Tile.Builder mvtBuilder = VectorTile.Tile.newBuilder();
        final IGeometryFilter acceptAllGeomFilter = geometry -> true;
        final Envelope tileEnvelope = new Envelope(se, nw);
        final MvtLayerParams layerParams = new MvtLayerParams(256, 4096);
        final UserDataKeyValueMapConverter converter = new UserDataKeyValueMapConverter();
        if (!encodingManager.hasEncodedValue(RoadClass.KEY))
            throw new IllegalStateException("You need to configure GraphHopper to store road_class, e.g. graph.encoded_values: road_class,max_speed,... ");

        final EnumEncodedValue<RoadClass> roadClassEnc = encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        final AtomicInteger edgeCounter = new AtomicInteger(0);
        // in toFeatures addTags of the converter is called and layerProps is filled with keys&values => those need to be stored in the layerBuilder
        // otherwise the decoding won't be successful and "undefined":"undefined" instead of "speed": 30 is the result
        final MvtLayerProps layerProps = new MvtLayerProps();
        final VectorTile.Tile.Layer.Builder layerBuilder = MvtLayerBuild.newLayerBuilder("roads", layerParams);

        locationIndex.query(bbox, edgeId -> {
            EdgeIteratorState edge = graphHopper.getBaseGraph().getEdgeIteratorStateForKey(edgeId * 2);
            LineString lineString;
            if (pathDetails.contains(UrbanDensity.KEY)) {
                if (zInfo >= 9) {
                    PointList pl = edge.fetchWayGeometry(FetchMode.ALL);
                    lineString = pl.toLineString(false);
                } else {
                    // skip edge for certain zoom
                    return;
                }
            } else {
                RoadClass rc = edge.get(roadClassEnc);
                if (zInfo >= 14) {
                    PointList pl = edge.fetchWayGeometry(FetchMode.ALL);
                    lineString = pl.toLineString(false);
                } else if (rc == RoadClass.MOTORWAY
                        || zInfo > 10 && (rc == RoadClass.PRIMARY || rc == RoadClass.TRUNK)
                        || zInfo > 11 && (rc == RoadClass.SECONDARY)
                        || zInfo > 12) {
                    double lat = na.getLat(edge.getBaseNode());
                    double lon = na.getLon(edge.getBaseNode());
                    double toLat = na.getLat(edge.getAdjNode());
                    double toLon = na.getLon(edge.getAdjNode());
                    lineString = geometryFactory.createLineString(new Coordinate[]{new Coordinate(lon, lat), new Coordinate(toLon, toLat)});
                } else {
                    // skip edge for certain zoom
                    return;
                }
            }

            edgeCounter.incrementAndGet();
            Map<String, Object> map = new HashMap<>(2);
            map.put("name", edge.getName());
            for (String str : pathDetails) {
                // how to indicate an erroneous parameter?
                if (str.contains(",") || !encodingManager.hasEncodedValue(str))
                    continue;

                EncodedValue ev = encodingManager.getEncodedValue(str, EncodedValue.class);
                if (ev instanceof EnumEncodedValue)
                    map.put(ev.getName(), edge.get((EnumEncodedValue) ev).toString());
                else if (ev instanceof DecimalEncodedValue)
                    map.put(ev.getName(), edge.get((DecimalEncodedValue) ev));
                else if (ev instanceof BooleanEncodedValue)
                    map.put(ev.getName(), edge.get((BooleanEncodedValue) ev));
                else if (ev instanceof IntEncodedValue)
                    map.put(ev.getName(), edge.get((IntEncodedValue) ev));
            }

            lineString.setUserData(map);

            // doing some AffineTransformation
            TileGeomResult tileGeom = JtsAdapter.createTileGeom(lineString, tileEnvelope, geometryFactory, layerParams, acceptAllGeomFilter);
            List<VectorTile.Tile.Feature> features = JtsAdapter.toFeatures(tileGeom.mvtGeoms, layerProps, converter);
            layerBuilder.addAllFeatures(features);
        });

        MvtLayerBuild.writeProps(layerBuilder, layerProps);
        mvtBuilder.addLayers(layerBuilder.build());
        byte[] bytes = mvtBuilder.build().toByteArray();
        totalSW.stop();
        logger.debug("took: " + totalSW.getSeconds() + ", edges:" + edgeCounter.get());
        return Response.ok(bytes, PBF).header("X-GH-Took", "" + totalSW.getSeconds() * 1000)
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
