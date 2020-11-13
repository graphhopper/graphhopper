package com.graphhopper.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.farmy.*;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;


@Path("optimize-routes")
public class OptimizeRoutesResource {

    private static final Logger logger = LoggerFactory.getLogger(RouteResource.class);

    private final GraphHopperAPI graphHopperAPI;

    @Inject
    public OptimizeRoutesResource(GraphHopperAPI graphHopperAPI) {
        this.graphHopperAPI = graphHopperAPI;
    }

    /**
     * @deprecated use json request instead
     */
    @Deprecated
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response doPost(@FormDataParam("orders") String farmyOrdersStr,
                           @FormDataParam("vehicles") String farmyVehicleStr,
                           @FormDataParam("startLocation") String depotPointStr) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        FarmyOrder[] farmyOrders = mapper.readValue(farmyOrdersStr, FarmyOrder[].class);
        FarmyVehicle[] farmyVehicles = mapper.readValue(farmyVehicleStr, FarmyVehicle[].class);

        IdentifiedGHPoint3D depotPoint = new IdentifiedGHPoint3D(mapper.readValue(depotPointStr, ArrayList.class), "Depot");

        RouteOptimizer routeOptimizer;
        try {
            routeOptimizer = new RouteOptimizer(this.graphHopperAPI, farmyOrders, farmyVehicles, depotPoint);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        return Response.ok().entity(routeOptimizer.getOptimizedRoutes().toString()).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response doPost(@Context HttpServletRequest httpReq) throws IOException {

        BufferedReader bufferedReader =  new BufferedReader(new InputStreamReader(httpReq.getInputStream()));
        String json = bufferedReader.readLine();

        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        ObjectMapper mapper = new ObjectMapper();

        FarmyOrder[] farmyOrders = mapper.readValue(jsonObject.get("orders").toString(), FarmyOrder[].class);
        FarmyVehicle[] farmyVehicles = mapper.readValue(jsonObject.get("vehicles").toString(), FarmyVehicle[].class);

        IdentifiedGHPoint3D depotPoint;
        String depotPointStr = jsonObject.get("startLocation").toString();
        if (depotPointStr.isEmpty()) {
            depotPoint = new IdentifiedGHPoint3D(mapper.readValue(depotPointStr, ArrayList.class), "Depot");
        } else {
            depotPoint = RoutePlanReader.depotPoint();
        }

        RouteOptimizer routeOptimizer;
        try {
            routeOptimizer = new RouteOptimizer(this.graphHopperAPI, farmyOrders, farmyVehicles, depotPoint);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        return Response.ok().entity(routeOptimizer.getOptimizedRoutes().toString()).build();
    }


}
