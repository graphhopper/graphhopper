package com.graphhopper.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.farmy.FarmyCourier;
import com.graphhopper.farmy.FarmyOrder;
import com.graphhopper.farmy.FarmyVehicle;
import com.graphhopper.farmy.RouteOptimize;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;


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

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response doPost(@FormDataParam("orders") String farmyOrdersStr,
                           @FormDataParam("vehicles") String farmyVehicleStr) throws IOException {
        FarmyOrder[] farmyOrders = new ObjectMapper().readValue(farmyOrdersStr, FarmyOrder[].class);
        FarmyVehicle[] farmyVehicles = new ObjectMapper().readValue(farmyVehicleStr, FarmyVehicle[].class);
        RouteOptimize routeOptimize = null;
        try {
            routeOptimize = new RouteOptimize(this.graphHopper, farmyOrders, farmyVehicles);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        return Response.ok().entity(routeOptimize.getOptimizedRoutes()).build();
    }


}
