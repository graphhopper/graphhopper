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
package com.graphhopper.http;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.xml.ws.WebServiceException;
import java.io.IOException;

/**
 * @author svantulden
 * @author Michael Zilske
 */
@Path("nearest")
@Produces(MediaType.APPLICATION_JSON)
public class Nearest {
    private final DistanceCalc calc = Helper.DIST_EARTH;
    @Inject
    private Provider<LocationIndex> index;
    @Inject
    @Named("hasElevation")
    private boolean hasElevation;

    public static class Response {
        public final String type = "Point";
        public final double[] coordinates;
        public final double distance; // Distance from input to snapped point in meters

        @JsonCreator
        public Response(@JsonProperty("coordinates") double[] coordinates, @JsonProperty("distance") double distance) {
            this.coordinates = coordinates;
            this.distance = distance;
        }
    }

    @GET
    public Response doGet(@QueryParam("point") GHPoint point, @QueryParam("elevation") @DefaultValue("false") boolean elevation) {
        QueryResult qr = index.get().findClosest(point.lat, point.lon, EdgeFilter.ALL_EDGES);
        if (qr.isValid()) {
            GHPoint3D snappedPoint = qr.getSnappedPoint();
            double[] coordinates = hasElevation && elevation ? new double[]{snappedPoint.lon, snappedPoint.lat, snappedPoint.ele} : new double[]{snappedPoint.lon, snappedPoint.lat};
            return new Response(coordinates, calc.calcDist(point.lat, point.lon, snappedPoint.lat, snappedPoint.lon));
        } else {
            throw new WebServiceException("Nearest point cannot be found!");
        }
    }

}
