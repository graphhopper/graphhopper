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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.jackson.MultiException;
import com.graphhopper.GHRequest;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.SnapPreventionEdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import java.util.List;
import com.graphhopper.util.PMap;

import static com.graphhopper.util.Parameters.Routing.SNAP_PREVENTION;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * @author svantulden
 * @author Michael Zilske
 * @author Michael Reichert
 */
@Path("nearest")
@Produces(MediaType.APPLICATION_JSON)
public class NearestResource {

    private final GraphHopper graphHopper;
    private final DistanceCalc calc = DistanceCalcEarth.DIST_EARTH;
    private final LocationIndex index;
    private final boolean hasElevation;
    private final List<String> snapPreventionsDefault;

    @Inject
    NearestResource(GraphHopperConfig config, GraphHopper hopper,
                    @Named("hasElevation") Boolean hasElevation) {
        this.graphHopper = hopper;
        this.index = hopper.getLocationIndex();
        this.hasElevation = hasElevation;
        this.snapPreventionsDefault = Arrays
                        .stream(config.getString("routing.snap_preventions_default", "").split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public static class Response {
        public final String type = "Point";
        public final double[] coordinates;
        public final double distance; // Distance from input to snapped point in meters

        @JsonCreator
        Response(@JsonProperty("coordinates") double[] coordinates, @JsonProperty("distance") double distance) {
            this.coordinates = coordinates;
            this.distance = distance;
        }
    }

    private Profile getProfile(GHRequest request) {
        Profile profile = graphHopper.getProfile(request.getProfile());
        if (profile == null) {
            List<String> availableProfiles = graphHopper.getProfiles().stream()
                            .map(Profile::getName).collect(Collectors.toList());
            throw new IllegalArgumentException("The requested profile '" + request.getProfile()
                            + "' does not exist.\nAvailable profiles: " + availableProfiles);
        }
        return profile;
    }

    private EdgeFilter createEdgeFilter(GHRequest request) {
        PMap requestHints = new PMap(request.getHints());
        Profile profile = getProfile(request);
        Weighting weighting = graphHopper.createWeighting(profile, requestHints, true);
        EdgeFilter filter = new DefaultSnapFilter(weighting, graphHopper.getEncodingManager()
                        .getBooleanEncodedValue(Subnetwork.key(profile.getName())));
        final EnumEncodedValue<RoadClass> roadClassEnc = graphHopper.getEncodingManager()
                        .getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        final EnumEncodedValue<RoadEnvironment> roadEnvEnc = graphHopper.getEncodingManager()
                        .getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        return request.getSnapPreventions().isEmpty() ? filter
                        : new SnapPreventionEdgeFilter(filter, roadClassEnc, roadEnvEnc, request.getSnapPreventions());
    }

    @GET
    public Response doGet(@Context UriInfo uriInfo, @QueryParam("point") GHPoint point,
                    @QueryParam("elevation") @DefaultValue("false") boolean elevation,
                    @QueryParam(SNAP_PREVENTION) List<String> snapPreventions,
                    @QueryParam("profile") String profileName) {
        GHRequest request = new GHRequest();
        RouteResource.initHints(request.getHints(), uriInfo.getQueryParameters());
        request.setProfile(profileName);

        if (uriInfo.getQueryParameters().containsKey(SNAP_PREVENTION)) {
            if (snapPreventions.size() == 1 && snapPreventions.contains(""))
                request.setSnapPreventions(List.of()); // e.g.
                                                       // "&snap_prevention=&"
                                                       // to force empty list
            else
                request.setSnapPreventions(snapPreventions);
        } else {
            // no "snap_prevention" was specified
            request.setSnapPreventions(snapPreventionsDefault);
        }

        EdgeFilter filter = profileName == null ? EdgeFilter.ALL_EDGES : createEdgeFilter(request);
        Snap snap = index.findClosest(point.lat, point.lon, filter);
        if (snap.isValid()) {
            GHPoint3D snappedPoint = snap.getSnappedPoint();
            double[] coordinates = hasElevation && elevation ? new double[]{snappedPoint.lon, snappedPoint.lat, snappedPoint.ele} : new double[]{snappedPoint.lon, snappedPoint.lat};
            return new Response(coordinates, calc.calcDist(point.lat, point.lon, snappedPoint.lat, snappedPoint.lon));
        } else {
            throw new MultiException(List.of(new PointNotFoundException("Point " + point + " is either out of bounds or cannot be found", 0)));
        }
    }

}
