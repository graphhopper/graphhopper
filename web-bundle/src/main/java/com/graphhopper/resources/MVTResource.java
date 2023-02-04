package com.graphhopper.resources;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.core.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.BBox;
import no.ecc.vectortile.VectorTileEncoder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.LinkedHashMap;
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
            @QueryParam("render_all") @DefaultValue("false") Boolean renderAll) {

        if (zInfo <= 9) {
            byte[] bytes = new VectorTileEncoder().encode();
            return Response.fromResponse(Response.ok(bytes, PBF).build())
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
        if (!encodingManager.hasEncodedValue(RoadClass.KEY))
            throw new IllegalStateException("You need to configure GraphHopper to store road_class, e.g. graph.encoded_values: road_class,max_speed,... ");

        final EnumEncodedValue<RoadClass> roadClassEnc = encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        final AtomicInteger edgeCounter = new AtomicInteger(0);

        // 256x256 pixels per MVT. here we transform from the global coordinate system to the local one of the tile.
        AffineTransformation affineTransformation = new AffineTransformation();
        affineTransformation.translate(-nw.x, -se.y);
        affineTransformation.scale(
                256.0 / (se.x - nw.x),
                -256.0 / (nw.y - se.y)
        );
        affineTransformation.translate(0, 256);

        // if performance of the vector tile encoding becomes an issue it might be worth to get rid of the simplification
        // and clipping in the no.ecc code? https://github.com/graphhopper/graphhopper/commit/0f96c2deddb24efa97109e35e0c05f1c91221f59#r90830001
        VectorTileEncoder vectorTileEncoder = new VectorTileEncoder();
        locationIndex.query(bbox, edgeId -> {
            EdgeIteratorState edge = graphHopper.getBaseGraph().getEdgeIteratorStateForKey(edgeId * 2);
            LineString lineString;
            if (renderAll) {
                PointList pl = edge.fetchWayGeometry(FetchMode.ALL);
                lineString = pl.toLineString(false);
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
            Map<String, Object> map = new LinkedHashMap<>();
            edge.getKeyValues().forEach(
                    entry -> map.put(entry.key, entry.value)
            );
            map.put("edge_id", edge.getEdge());
            map.put("edge_key", edge.getEdgeKey());
            map.put("base_node", edge.getBaseNode());
            map.put("adj_node", edge.getAdjNode());
            map.put("distance", edge.getDistance());
            encodingManager.getEncodedValues().forEach(ev -> {
                if (ev instanceof EnumEncodedValue)
                    map.put(ev.getName(), edge.get((EnumEncodedValue) ev).toString() + (ev.isStoreTwoDirections() ? " | " + edge.getReverse((EnumEncodedValue) ev).toString() : ""));
                else if (ev instanceof DecimalEncodedValue)
                    map.put(ev.getName(), edge.get((DecimalEncodedValue) ev) + (ev.isStoreTwoDirections() ? " | " + edge.getReverse((DecimalEncodedValue) ev) : ""));
                else if (ev instanceof BooleanEncodedValue)
                    map.put(ev.getName(), edge.get((BooleanEncodedValue) ev) + (ev.isStoreTwoDirections() ? " | " + edge.getReverse((BooleanEncodedValue) ev) : ""));
                else if (ev instanceof IntEncodedValue)
                    map.put(ev.getName(), edge.get((IntEncodedValue) ev) + (ev.isStoreTwoDirections() ? " | " + edge.getReverse((IntEncodedValue) ev) : ""));
            });
            lineString.setUserData(map);

            Geometry g = affineTransformation.transform(lineString);
            vectorTileEncoder.addFeature("roads", map, g, edge.getEdge());
        });


        byte[] bytes = vectorTileEncoder.encode();
        totalSW.stop();
        logger.debug("took: " + totalSW.getMillis() + "ms, edges:" + edgeCounter.get());
        return Response.ok(bytes, PBF).header("X-GH-Took", "" + totalSW.getSeconds() * 1000)
                .build();
    }

    Coordinate num2deg(int xInfo, int yInfo, int zoom) {
        // inverse web mercator projection
        double n = Math.pow(2, zoom);
        double lonDeg = xInfo / n * 360.0 - 180.0;
        // unfortunately latitude numbers goes from north to south
        double latRad = Math.atan(Math.sinh(Math.PI * (1 - 2 * yInfo / n)));
        double latDeg = Math.toDegrees(latRad);
        return new Coordinate(lonDeg, latDeg);
    }
}
