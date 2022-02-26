package com.graphhopper.resources;

import com.conveyal.gtfs.model.Stop;
import com.google.protobuf.ByteString;
import com.graphhopper.GraphHopper;
import com.graphhopper.gtfs.GtfsStorage;
import com.graphhopper.gtfs.PtGraph;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.BBox;
import com.wdtinc.mapbox_vector_tile.VectorTile;
import com.wdtinc.mapbox_vector_tile.adapt.jts.JtsAdapter;
import com.wdtinc.mapbox_vector_tile.adapt.jts.TileGeomResult;
import com.wdtinc.mapbox_vector_tile.adapt.jts.UserDataKeyValueMapConverter;
import com.wdtinc.mapbox_vector_tile.build.MvtLayerBuild;
import com.wdtinc.mapbox_vector_tile.build.MvtLayerParams;
import com.wdtinc.mapbox_vector_tile.build.MvtLayerProps;
import org.locationtech.jts.geom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Path("pt-mvt")
@Singleton
public class PtMVTResource {

    private static final Logger logger = LoggerFactory.getLogger(PtMVTResource.class);
    private static final MediaType PBF = new MediaType("application", "x-protobuf");
    private final GraphHopper graphHopper;
    private final GtfsStorage gtfsStorage;
    private final Map<ByteString, MatchResult> openLRCache = new ConcurrentHashMap<>();
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Inject
    public PtMVTResource(GraphHopper graphHopper, GtfsStorage gtfsStorage) throws IOException {
        this.graphHopper = graphHopper;
        this.gtfsStorage = gtfsStorage;
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

        Coordinate nw = num2deg(xInfo, yInfo, zInfo);
        Coordinate se = num2deg(xInfo + 1, yInfo + 1, zInfo);
        BBox bbox = new BBox(nw.x, se.x, se.y, nw.y);
        if (!bbox.isValid())
            throw new IllegalStateException("Invalid bbox " + bbox);

        List<Geometry> features = new ArrayList<>();
        gtfsStorage.getStopIndex().query(bbox, edgeId -> {
            for (PtGraph.PtEdge ptEdge : gtfsStorage.getPtGraph().backEdgesAround(edgeId)) {
                if (ptEdge.getType() == GtfsStorage.EdgeType.EXIT_PT) {
                    GtfsStorage.PlatformDescriptor fromPlatformDescriptor = ptEdge.getAttrs().platformDescriptor;
                    Stop stop = gtfsStorage.getGtfsFeeds().get(fromPlatformDescriptor.feed_id).stops.get(fromPlatformDescriptor.stop_id);
                    Map<String, Object> properties = new HashMap<>(2);
                    properties.put("feed_id", fromPlatformDescriptor.feed_id);
                    properties.put("stop_id", fromPlatformDescriptor.stop_id);
                    Point feature = geometryFactory.createPoint(new Coordinate(stop.stop_lon, stop.stop_lat));
                    feature.setUserData(properties);
                    features.add(feature);
                }
            }
        });
        VectorTile.Tile.Builder mvtBuilder = VectorTile.Tile.newBuilder();
        mvtBuilder.addLayers(createLayer(new Envelope(se, nw), new MvtLayerParams(256, 4096), features, "stops"));
        return Response.ok(mvtBuilder.build().toByteArray(), PBF).build();
    }

    private VectorTile.Tile.Layer createLayer(Envelope tileEnvelope, MvtLayerParams layerParams, List<Geometry> locationReferences, String layerName) {
        final VectorTile.Tile.Layer.Builder roadsLayerBuilder = MvtLayerBuild.newLayerBuilder(layerName, layerParams);
        TileGeomResult tileGeom = JtsAdapter.createTileGeom(locationReferences, tileEnvelope, geometryFactory, layerParams, geometry -> true);
        final MvtLayerProps roadsLayerProps = new MvtLayerProps();
        roadsLayerBuilder.addAllFeatures(JtsAdapter.toFeatures(tileGeom.mvtGeoms, roadsLayerProps, new UserDataKeyValueMapConverter()));
        MvtLayerBuild.writeProps(roadsLayerBuilder, roadsLayerProps);
        return roadsLayerBuilder.build();
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
