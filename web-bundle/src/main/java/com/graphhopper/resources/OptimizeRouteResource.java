package com.graphhopper.resources;

import com.graphhopper.GraphHopperAPI;
import com.graphhopper.farmygh.RouteOptimize;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("optimize-route")
public class OptimizeRouteResource {

    private static final Logger logger = LoggerFactory.getLogger(RouteResource.class);

    private final GraphHopperAPI graphHopper;
    private final Boolean hasElevation;

    @Inject
    public OptimizeRouteResource(GraphHopperAPI graphHopper, @Named("hasElevation") Boolean hasElevation) {
        this.graphHopper = graphHopper;
        this.hasElevation = hasElevation;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response doGet() {

        RouteOptimize routeOptimize = null;
        try {
            routeOptimize = new RouteOptimize(this.graphHopper);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        return Response.ok(routeOptimize.getOptimizedRoutes()).build();
    }
}
