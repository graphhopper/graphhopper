package com.graphhopper.resources;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.graphhopper.GraphHopper;
import com.graphhopper.http.GHPointParam;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.StopWatch;
import com.graphhopper.buffer.BufferFeature;

import org.locationtech.jts.geom.LineString;

@Path("buffer")
public class BufferResource {
    private BufferUpstreamShared bufferUpstreamShared;

    @Inject
    public BufferResource(GraphHopper graphHopper) {
        bufferUpstreamShared = new BufferUpstreamShared(graphHopper);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGet(
            @QueryParam("point") @NotNull GHPointParam point,
            @QueryParam("roadName") @NotNull String roadName,
            @QueryParam("thresholdDistance") @NotNull Double thresholdDistance,
            @QueryParam("queryMultiplier") @DefaultValue(".01") Double queryMultiplier,
            @QueryParam("buildUpstream") @DefaultValue("false") Boolean buildUpstream) {
        if (queryMultiplier > 1) {
            throw new IllegalArgumentException("Query multiplier is too high.");
        } else if (queryMultiplier <= 0) {
            throw new IllegalArgumentException("Query multiplier cannot be zero or negative.");
        }

        StopWatch sw = new StopWatch().start();

        roadName = bufferUpstreamShared.sanitizeRoadNames(roadName)[0];
        BufferFeature primaryStartFeature = bufferUpstreamShared.calculatePrimaryStartFeature(point.get().lat,
                point.get().lon, roadName,
                queryMultiplier);
        EdgeIteratorState state = bufferUpstreamShared.getEdgeIteratorState(primaryStartFeature);
        List<LineString> lineStrings = new ArrayList<LineString>();

        // Start feature edge is bidirectional. Simple
        if (bufferUpstreamShared.isBidirectional(state)) {
            lineStrings.add(bufferUpstreamShared.computeBufferSegment(primaryStartFeature, roadName, thresholdDistance,
                    buildUpstream, true));
            lineStrings.add(bufferUpstreamShared.computeBufferSegment(primaryStartFeature, roadName, thresholdDistance,
                    buildUpstream, false));
        }
        // Start feature edge is unidirectional. Requires finding sister road
        else {
            BufferFeature secondaryStartFeature = bufferUpstreamShared
                    .calculateSecondaryStartFeature(primaryStartFeature, roadName, .005);
            lineStrings.add(bufferUpstreamShared.computeBufferSegment(primaryStartFeature, roadName, thresholdDistance,
                    buildUpstream, buildUpstream));
            lineStrings.add(bufferUpstreamShared.computeBufferSegment(secondaryStartFeature, roadName,
                    thresholdDistance, buildUpstream, buildUpstream));
        }

        return bufferUpstreamShared.createGeoJsonResponse(lineStrings, sw);
    }

}