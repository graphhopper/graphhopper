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

import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.jackson.CustomRequest;
import com.graphhopper.jackson.MultiException;
import com.graphhopper.jackson.ResponsePathSerializer;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.graphhopper.util.Parameters.Routing.*;

/**
 * Routing resource to use GraphHopper in a remote client application like mobile or browser. This endpoint allows
 * specifying a custom model on a per-request basis (and thus only works for hybrid and flex mode).
 * <p>
 * Note: This endpoint returns the points in GeoJson array format [longitude,latitude] unlike the format "lat,lon"
 * used for the request. See the full API response format in docs/web/api-doc.md
 *
 * @author Peter Karich
 */
@Path("route-custom")
public class CustomWeightingRouteResource {

    private static final Logger logger = LoggerFactory.getLogger(CustomWeightingRouteResource.class);

    private final GraphHopper graphHopper;

    @Inject
    public CustomWeightingRouteResource(GraphHopper graphHopper) {
        this.graphHopper = graphHopper;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response doPost(@NotNull CustomRequest request, @Context HttpServletRequest httpReq) {
        StopWatch sw = new StopWatch().start();
        CustomModel model = request.getModel();
        if (model == null)
            throw new IllegalArgumentException("No custom model properties found");
        if (request.getHints().has(BLOCK_AREA))
            throw new IllegalArgumentException("Instead of block_area define the geometry under 'areas' as GeoJSON and use 'area_<id>: 0' in e.g. priority");
        if (!request.getHints().getBool(Parameters.CH.DISABLE, true))
            throw new IllegalArgumentException("Custom requests are not available for speed mode, do not use ch.disable=false");
        if (Helper.isEmpty(request.getProfile()))
            throw new IllegalArgumentException("The 'profile' parameter for CustomRequest is required");

        Profile profile = graphHopper.getProfile(request.getProfile());
        if (profile == null)
            throw new IllegalArgumentException("profile '" + request.getProfile() + "' not found");
        if (!(profile instanceof CustomProfile))
            throw new IllegalArgumentException("profile '" + request.getProfile() + "' cannot be used for a custom request because it has weighting=" + profile.getWeighting());

        request.putHint(Parameters.CH.DISABLE, true);
        request.putHint(CustomModel.KEY, model);
        GHResponse ghResponse = graphHopper.route(request);

        boolean instructions = request.getHints().getBool(INSTRUCTIONS, true);
        boolean enableElevation = request.getHints().getBool("elevation", false);
        boolean calcPoints = request.getHints().getBool(CALC_POINTS, true);
        boolean pointsEncoded = request.getHints().getBool("points_encoded", true);

        long took = sw.stop().getNanos() / 1_000_000;
        String infoStr = httpReq.getRemoteAddr() + " " + httpReq.getLocale() + " " + httpReq.getHeader("User-Agent");
        String queryString = httpReq.getQueryString() == null ? "" : (httpReq.getQueryString() + " ");
        String logStr = queryString + infoStr + " " + request.getPoints().size() + ", took: "
                + String.format("%.1f", (double) took) + " ms, algo: " + request.getAlgorithm() + ", profile: " + request.getProfile()
                + ", custom_model=" + model;

        if (ghResponse.hasErrors()) {
            logger.error(logStr + ", errors:" + ghResponse.getErrors());
            throw new MultiException(ghResponse.getErrors());
        } else {
            logger.info(logStr + ", alternatives: " + ghResponse.getAll().size()
                    + ", distance0: " + ghResponse.getBest().getDistance()
                    + ", weight0: " + ghResponse.getBest().getRouteWeight()
                    + ", time0: " + Math.round(ghResponse.getBest().getTime() / 60000f) + "min"
                    + ", points0: " + ghResponse.getBest().getPoints().getSize()
                    + ", debugInfo: " + ghResponse.getDebugInfo());
            return Response.ok(ResponsePathSerializer.jsonObject(ghResponse, instructions, calcPoints, enableElevation, pointsEncoded, took)).
                    header("X-GH-Took", "" + Math.round(took)).
                    build();
        }
    }
}
