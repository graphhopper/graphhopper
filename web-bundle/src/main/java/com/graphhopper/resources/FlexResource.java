package com.graphhopper.resources;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.MultiException;
import com.graphhopper.http.WebHelper;
import com.graphhopper.routing.flex.FlexModel;
import com.graphhopper.routing.flex.FlexRequest;
import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;
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

@Path("flex")
public class FlexResource {
    private static final Logger logger = LoggerFactory.getLogger(RouteResource.class);

    private GraphHopper graphHopper;
    private Boolean hasElevation;

    @Inject
    public FlexResource(GraphHopper graphHopper, @Named("hasElevation") Boolean hasElevation) {
        this.graphHopper = graphHopper;
        this.hasElevation = hasElevation;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/gpx+xml"})
    public Response doPost(FlexRequest flex) {
        if (flex == null)
            throw new IllegalArgumentException("FlexRequest cannot be empty");
        FlexModel model = flex.getModel();
        GHRequest request = flex.getRequest();

        // TODO move this validiation into FlexModel?
        if (Helper.isEmpty(model.getBase()))
            throw new IllegalArgumentException("'base' cannot be empty");
        if (model.getMaxSpeed() < 1)
            throw new IllegalArgumentException("max_speed too low: " + model.getMaxSpeed());

        request.setVehicle(model.getBase());
        request.getHints().put("ch.disable", true);

        // TODO read from FlexRequest? like query.getInstructions()
        boolean instructions = true;
        boolean calcPoints = true;
        boolean enableElevation = false;
        boolean pointsEncoded = true;

        StopWatch sw = new StopWatch().start();
        GHResponse ghResponse = new GHResponse();
        graphHopper.calcPaths(request, model, ghResponse);
        float took = sw.stop().getSeconds();
        if (ghResponse.hasErrors()) {
            logger.error("errors:" + ghResponse.getErrors());
            throw new MultiException(ghResponse.getErrors());
        } else {
            logger.info("alternatives: " + ghResponse.getAll().size()
                    + ", distance0: " + ghResponse.getBest().getDistance()
                    + ", weight0: " + ghResponse.getBest().getRouteWeight()
                    + ", time0: " + Math.round(ghResponse.getBest().getTime() / 60000f) + "min"
                    + ", points0: " + ghResponse.getBest().getPoints().getSize()
                    + ", debugInfo: " + ghResponse.getDebugInfo());
            return Response.ok(WebHelper.jsonObject(ghResponse, instructions, calcPoints, enableElevation, pointsEncoded, took)).
                    header("X-GH-Took", "" + Math.round(took * 1000)).
                    build();
        }
    }
}
