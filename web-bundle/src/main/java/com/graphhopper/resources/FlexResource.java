/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.MultiException;
import com.graphhopper.http.WebHelper;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.util.FlexModel;
import com.graphhopper.routing.util.FlexRequest;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("flex")
public class FlexResource {
    private static final Logger logger = LoggerFactory.getLogger(RouteResource.class);

    private final GraphHopper graphHopper;
    private final ObjectMapper yamlOM;

    @Inject
    public FlexResource(GraphHopper graphHopper, CmdArgs cmdArgs) {
        this.graphHopper = graphHopper;
        this.yamlOM = Jackson.initObjectMapper(new ObjectMapper(new YAMLFactory()));
    }

    @POST
    @Consumes({"text/x-yaml", "application/x-yaml", "application/yaml"})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/gpx+xml"})
    public Response doPost(String yaml) {
        FlexRequest flexRequest;
        try {
            flexRequest = yamlOM.readValue(yaml, FlexRequest.class);
        } catch (Exception ex) {
            // TODO should we really provide this much details to API users?
            throw new IllegalArgumentException("Incorrect YAML: " + ex.getMessage(), ex);
        }
        return doPost(flexRequest);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/gpx+xml"})
    public Response doPost(FlexRequest request) {
        if (request == null)
            throw new IllegalArgumentException("FlexRequest cannot be empty");

        FlexModel model = request.getModel();
        if (model.getMaxSpeed() < 1)
            model.setMaxSpeed((request.getWeighting().startsWith("car") || request.getWeighting().startsWith("truck")) ? 100 : 10);

        request.setWeighting("flex");
        request.getHints().put("ch.disable", true);

        // TODO NOW read from FlexRequest and make compatible to POST /route or move the FlexModel creation to POST /route?
        boolean instructions = true;
        boolean calcPoints = true;
        boolean enableElevation = false;
        boolean pointsEncoded = true;

        StopWatch sw = new StopWatch().start();
        GHResponse ghResponse = new GHResponse();
        graphHopper.calcPaths(request, ghResponse, model);

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