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

import com.conveyal.osmlib.OSM;
import com.conveyal.osmlib.OSMEntity;
import com.conveyal.osmlib.Relation;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.StreamingOutput;
import java.io.PrintWriter;
import java.util.Map;

@Path("restrictions")
public class RestrictionsResource {

    private final OSM osm;

    @Inject
    public RestrictionsResource(OSM osm) {
        this.osm = osm;
    }

    @GET
    @Produces("text/plain")
    public StreamingOutput conditionalRelations() {
        return output -> {
            PrintWriter printWriter = new PrintWriter(output);
            for (Map.Entry<Long, Relation> entry : osm.relations.entrySet()) {
                if (entry.getValue().hasTag("type", "restriction")) {
                    for (OSMEntity.Tag tag : entry.getValue().tags) {
                        if (tag.key.equals("type")) continue;
                        printWriter.println(entry.getKey() + "\t" + tag);
                    }
                }
            }
            printWriter.flush();
        };
    }

}
