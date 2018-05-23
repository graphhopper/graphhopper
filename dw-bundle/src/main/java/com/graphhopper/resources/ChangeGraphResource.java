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

import com.codahale.metrics.annotation.Timed;
import com.graphhopper.GraphHopper;
import com.graphhopper.json.geo.JsonFeatureCollection;
import org.glassfish.jersey.server.ManagedAsync;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;

/**
 * This class defines a new endpoint to submit access and speed changes to the graph.
 *
 * @author Peter Karich
 * @author Michael Zilske
 *
 */
@Path("change")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChangeGraphResource {

    private GraphHopper graphHopper;

    @Inject
    ChangeGraphResource(GraphHopper graphHopper) {
        this.graphHopper = graphHopper;
    }

    @POST
    @Timed
    @ManagedAsync
    public void changeGraph(JsonFeatureCollection collection, @Suspended AsyncResponse response) {
        response.resume(graphHopper.changeGraph(collection.getFeatures()));
    }

}
