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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.*;
import com.graphhopper.routing.EdgeRestrictions;
import com.graphhopper.routing.PathCalculator;
import com.graphhopper.routing.ProfileResolver;
import com.graphhopper.routing.Router;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.*;

import static com.graphhopper.util.Parameters.Routing.*;

/**
 * Resource to use GraphHopper in a remote client application like mobile or browser. Note: If type
 * is json it returns the points in GeoJson array format [longitude,latitude] unlike the format "lat,lon"
 * used for the request. See the full API response format in docs/web/api-doc.md
 *
 * @author Muhammad Salar Khan
 */
@Path("distance-matrix")
public class DistanceMatrixResource {

    private final ProfileResolver profileResolver;
    private final Router router;

    @Inject
    public DistanceMatrixResource(GraphHopper graphHopper, ProfileResolver profileResolver) {
        this.profileResolver = profileResolver;
        this.router = graphHopper.createRouter();
    }

    public double getDistanceFor(List<com.graphhopper.routing.Path> paths) {
        double toReturn = 0;
        for(com.graphhopper.routing.Path p: paths) {
            toReturn += p.getDistance();
        }
        return toReturn;
    }

    public long getTimeFor(List<com.graphhopper.routing.Path> paths) {
        long toReturn = 0;
        for(com.graphhopper.routing.Path p: paths) {
            toReturn += p.getTime();
        }
        return toReturn;
    }

    @POST
    @Path("request")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMatrix(@NotNull GHRequestDistanceMatrix request) {
        if(request.getOrigins().size() == 0 || request.getDestinations().size() == 0) {
            return Response.ok().
                    type(MediaType.APPLICATION_JSON).
                    build();
        }

        double[][] distanceMatrix = new double[request.getOrigins().size()][request.getDestinations().size()];
        long[][] etaMatrix = new long[request.getOrigins().size()][request.getDestinations().size()];


        // Create solver.
        Router.Solver solver = router.createAndInitSolver(getGhRequestObj());

        // Create origin and destination point list.
        List<Snap> origins = getSnapsListFromLatLngString(request.getOrigins(), router, solver);
        List<Snap> destinations = getSnapsListFromLatLngString(request.getDestinations(), router, solver);


        EdgeRestrictions ers = new EdgeRestrictions();

        // Nested Loop to populate the distance matrix.
        for(int i=0;i<origins.size();i++) {
            for (int j = 0; j < destinations.size(); j++) {
                Snap fromSnap = origins.get(i);
                Snap toSnap = destinations.get(j);

                PathCalculator pathCalculator =  router.createPathCalculatorForSnaps(Arrays.asList(fromSnap,toSnap), solver);
                List<com.graphhopper.routing.Path> paths = pathCalculator.calcPaths(fromSnap.getClosestNode(), toSnap.getClosestNode(), ers);

                distanceMatrix[i][j] = getDistanceFor(paths);
                etaMatrix[i][j] = getTimeFor(paths) / 1000;
            }
        }

        ObjectNode jsonResponseObj = JsonNodeFactory.instance.objectNode();
        jsonResponseObj.putPOJO("distance_matrix", distanceMatrix);
        jsonResponseObj.putPOJO("eta_matrix", etaMatrix);

        return Response.ok(jsonResponseObj).
                type(MediaType.APPLICATION_JSON).
                build();
    }

    private List<Snap> getSnapsListFromLatLngString(List<String> latLngStrings, Router router, Router.Solver solver) {
        List<Snap> toReturn = new ArrayList<>();
        for(String s: latLngStrings) {
            toReturn.add(
                    router.getSnap(GHPoint.fromString(s), solver)
            );
        }
        return toReturn;
    }

    private GHRequest getGhRequestObj() {
        GHRequest ghRequest = new GHRequest();
        String profileName = profileResolver.resolveProfile(ghRequest.getHints()).getName();
        ghRequest.setProfile(profileName).
                setAlgorithm("").
                setLocale("en").
                getHints().
                putObject(CALC_POINTS, false).
                putObject(INSTRUCTIONS, false).
                putObject(WAY_POINT_MAX_DISTANCE, "1");
        return ghRequest;
    }

    public static void errorIfLegacyParameters(PMap hints) {
        if (hints.has("weighting"))
            throw new IllegalArgumentException("Since you are using the 'profile' parameter, do not use the 'weighting' parameter." +
                    " You used 'weighting=" + hints.getString("weighting", "") + "'");
        if (hints.has("vehicle"))
            throw new IllegalArgumentException("Since you are using the 'profile' parameter, do not use the 'vehicle' parameter." +
                    " You used 'vehicle=" + hints.getString("vehicle", "") + "'");
        if (hints.has("edge_based"))
            throw new IllegalArgumentException("Since you are using the 'profile' parameter, do not use the 'edge_based' parameter." +
                    " You used 'edge_based=" + hints.getBool("edge_based", false) + "'");
        if (hints.has("turn_costs"))
            throw new IllegalArgumentException("Since you are using the 'profile' parameter, do not use the 'turn_costs' parameter." +
                    " You used 'turn_costs=" + hints.getBool("turn_costs", false) + "'");
    }

    public static void removeLegacyParameters(PMap hints) {
        // these parameters should only be used to resolve the profile, but should not be passed to GraphHopper
        hints.remove("weighting");
        hints.remove("vehicle");
        hints.remove("edge_based");
        hints.remove("turn_costs");
    }

    static void initHints(PMap m, MultivaluedMap<String, String> parameterMap) {
        for (Map.Entry<String, List<String>> e : parameterMap.entrySet()) {
            if (e.getValue().size() == 1) {
                m.putObject(Helper.camelCaseToUnderScore(e.getKey()), Helper.toObject(e.getValue().get(0)));
            } else {
                // TODO e.g. 'point' parameter occurs multiple times and we cannot throw an exception here
                //  unknown parameters (hints) should be allowed to be multiparameters, too, or we shouldn't use them for
                //  known parameters either, _or_ known parameters must be filtered before they come to this code point,
                //  _or_ we stop passing unknown parameters altogether.
                // throw new WebApplicationException(String.format("This query parameter (hint) is not allowed to occur multiple times: %s", e.getKey()));
                // see also #1976
            }
        }
    }
}
