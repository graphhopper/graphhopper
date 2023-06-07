package com.graphhopper.resources;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadClassLink;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.RoadDensityCalculator;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.BBox;
import org.locationtech.jts.geom.LineString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Path("urban-density")
public class UrbanDensityResource {
    private static final Logger logger = LoggerFactory.getLogger(UrbanDensityResource.class);
    private final GraphHopper graphHopper;
    private final EncodingManager encodingManager;

    @Inject
    public UrbanDensityResource(GraphHopper graphHopper, EncodingManager encodingManager) {
        this.graphHopper = graphHopper;
        this.encodingManager = encodingManager;
    }

    @GET
    @Produces("application/json")
    public List<Map<String, Object>> getUrbanDensityEdges(
            @Context HttpServletRequest httpReq,
            @Context UriInfo uriInfo,
            @QueryParam("min_lon") Double minLon,
            @QueryParam("min_lat") Double minLat,
            @QueryParam("max_lon") Double maxLon,
            @QueryParam("max_lat") Double maxLat,
            @QueryParam("radius") @DefaultValue("300") Double radius,
            @QueryParam("sensitivity") @DefaultValue("300") Double sensitivity,
            @QueryParam("render_all") @DefaultValue("false") Boolean renderAll) {

        StopWatch totalSW = new StopWatch().start();
        LocationIndexTree locationIndex = (LocationIndexTree) graphHopper.getLocationIndex();
        BBox bbox = new BBox(minLon, maxLon, minLat, maxLat);
        if (!bbox.isValid())
            throw new IllegalStateException("Invalid bbox " + bbox);

        if (!encodingManager.hasEncodedValue(RoadClass.KEY))
            throw new IllegalStateException("You need to configure GraphHopper to store road_class, e.g. graph.encoded_values: road_class,max_speed,... ");
        if (!encodingManager.hasEncodedValue(RoadClassLink.KEY))
            throw new IllegalStateException("You need to configure GraphHopper to store road_class_link, e.g. graph.encoded_values: road_class_link,max_speed,... ");

        final EnumEncodedValue<RoadClass> roadClassEnc = encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        final BooleanEncodedValue roadClassLinkEnc = encodingManager.getBooleanEncodedValue(RoadClassLink.KEY);
        final AtomicInteger edgeCounter = new AtomicInteger(0);

        List<Integer> edgeIds = new ArrayList<>();
        locationIndex.query(bbox, edgeIds::add);
        ThreadLocal<RoadDensityCalculator> calculator = ThreadLocal.withInitial(() -> new RoadDensityCalculator(graphHopper.getBaseGraph()));
        List<Map<String, Object>> features = edgeIds.parallelStream().map(edgeId -> {
            EdgeIteratorState edge = graphHopper.getBaseGraph().getEdgeIteratorStateForKey(edgeId * 2);
            double roadDensity = calculator.get().calcRoadDensity(edge, radius, e -> {
                if (e.get(roadClassLinkEnc) || e.get(roadClassEnc) == RoadClass.TRACK || e.get(roadClassEnc) == RoadClass.SERVICE
                        || e.get(roadClassEnc) == RoadClass.PATH || e.get(roadClassEnc) == RoadClass.BRIDLEWAY)
                    return 0;
                else
                    return 1;
            });
            boolean isResidential = roadDensity * sensitivity >= 1.0;
            PointList pl = edge.fetchWayGeometry(FetchMode.ALL);
            LineString lineString = pl.toLineString(false);

            edgeCounter.incrementAndGet();
            Map<String, Object> map = new LinkedHashMap<>();
            edge.getKeyValues().forEach(
                    entry -> map.put(entry.key, entry.value)
            );
            map.put("edge_id", edge.getEdge());
            map.put("residential", isResidential);
            Map<String, Object> feature = new HashMap<>();
            feature.put("linestring", lineString);
            feature.put("properties", map);
            return feature;
        }).collect(Collectors.toList());
        totalSW.stop();
        logger.info("took: " + totalSW.getMillis() + "ms, edges:" + edgeCounter.get());
        return features;
    }
}
