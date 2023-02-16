package com.graphhopper.resources;

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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

@Path("upstream")
public class UpstreamResource {
    private BufferUpstreamShared bufferUpstreamShared;

    @Inject
    public UpstreamResource(GraphHopper graphHopper) {
        bufferUpstreamShared = new BufferUpstreamShared(graphHopper);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGet(
            @QueryParam("pointA") @NotNull GHPointParam pointA,
            @QueryParam("pointB") @NotNull GHPointParam pointB,
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
        BufferFeature primaryStartFeature = bufferUpstreamShared.calculatePrimaryStartFeature(pointA.get().lat,
                pointA.get().lon, roadName, queryMultiplier);
        EdgeIteratorState state = bufferUpstreamShared.getEdgeIteratorState(primaryStartFeature);
        List<LineString> lineStrings = bufferUpstreamShared.calculateBuffer(state, primaryStartFeature, roadName,
                thresholdDistance, buildUpstream);

        // Calculate upstream and only returning the upstream path
        Coordinate coordA = new Coordinate(pointA.get().lon, pointA.get().lat);
        Coordinate coordB = new Coordinate(pointB.get().lon, pointB.get().lat);
        lineStrings = calculateUpstream(lineStrings, coordA, coordB);

        return bufferUpstreamShared.createGeoJsonResponse(lineStrings, sw);
    }

    /**
     * Calculates the upstream LineString based on the points on the road. If the
     * points exist on the same LineString,
     * we return the opposite because that is considered to be 'upstream' of the
     * road.
     * 
     * @param lineStrings the buffered LineStrings from pointA
     * @param pointA      first point in direction of the road
     * @param pointB      second point in the direction of the road
     * 
     * @return LineString representation of 'upstream' from the road
     */
    private List<LineString> calculateUpstream(List<LineString> lineStrings, Coordinate pointA, Coordinate pointB) {
        Coordinate[] coords = lineStrings.get(0).getCoordinates();
        if (coords[0] == pointA && coords[1] == pointB)
            lineStrings.remove(0);
        else
            lineStrings.remove(1);
        return lineStrings;
    }
}