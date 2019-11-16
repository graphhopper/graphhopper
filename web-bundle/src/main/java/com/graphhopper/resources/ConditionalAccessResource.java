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
import com.conveyal.osmlib.Way;
import com.graphhopper.TimeDependentAccessRestriction;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.parsers.OSMIDParser;
import com.graphhopper.storage.GraphHopperStorage;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.StreamingOutput;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.Map;

@Path("conditional-access")
public class ConditionalAccessResource {

    private final GraphHopperStorage storage;

    @Inject
    public ConditionalAccessResource(GraphHopperStorage storage) {
        this.storage = storage;
    }

    @GET
    @Produces("text/plain")
    public StreamingOutput conditionalRelations() {
        Instant linkEnterTime = Instant.now();
        TimeDependentAccessRestriction timeDependentAccessRestriction = new TimeDependentAccessRestriction(storage);
        return output -> {
            PrintWriter printWriter = new PrintWriter(output);
            printWriter.println(linkEnterTime);
            printWriter.println();
            final OSMIDParser osmidParser = OSMIDParser.fromEncodingManager(storage.getEncodingManager());
            final BooleanEncodedValue property = storage.getEncodingManager().getBooleanEncodedValue("conditional");
            OSM osm = storage.getOsm();
            AllEdgesIterator allEdges = storage.getAllEdges();
            while (allEdges.next()) {
                if (allEdges.get(property)) {
                    long osmid = osmidParser.getOSMID(allEdges.getFlags());
                    Way way = storage.getOsm().ways.get(osmid);
                    printWriter.printf("%d %s\n", osmid, timeDependentAccessRestriction.accessible(allEdges, linkEnterTime));
                }
            }
            printWriter.flush();
        };
    }

}
