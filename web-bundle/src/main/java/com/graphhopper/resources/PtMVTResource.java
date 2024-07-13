package com.graphhopper.resources;

import com.conveyal.gtfs.model.Stop;
import com.google.protobuf.ByteString;
import com.graphhopper.GraphHopper;
import com.graphhopper.gtfs.GtfsStorage;
import com.graphhopper.gtfs.PtGraph;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.BBox;
import no.ecc.vectortile.VectorTileEncoder;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import javax.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
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

        VectorTileEncoder vectorTileEncoder = new VectorTileEncoder();
        // 256x256 pixels per MVT. here we transform from the global coordinate system to the local one of the tile.
        AffineTransformation affineTransformation = new AffineTransformation();
        affineTransformation.translate(-nw.x, -se.y);
        affineTransformation.scale(
                256.0 / (se.x - nw.x),
                -256.0 / (nw.y - se.y)
        );
        affineTransformation.translate(0, 256);
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
                    Geometry g = affineTransformation.transform(feature);
                    vectorTileEncoder.addFeature("stops", properties, g);
                }
            }
        });
        return Response.ok(vectorTileEncoder.encode(), PBF).build();
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
