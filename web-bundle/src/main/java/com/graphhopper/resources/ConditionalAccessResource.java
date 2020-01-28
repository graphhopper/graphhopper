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

import ch.poole.openinghoursparser.Rule;
import com.conveyal.osmlib.OSM;
import com.conveyal.osmlib.Way;
import com.graphhopper.TimeDependentAccessRestriction;
import com.graphhopper.reader.ReaderWay;
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
import java.time.ZonedDateTime;
import java.util.List;

@Path("conditional-access")
public class ConditionalAccessResource {

    private final GraphHopperStorage storage;
    private final OSM osm;

    @Inject
    public ConditionalAccessResource(GraphHopperStorage storage, OSM osm) {
        this.storage = storage;
        this.osm = osm;
    }

    @GET
    @Produces("text/plain")
    public StreamingOutput conditionalRelations() {
        Instant linkEnterTime = Instant.now();
        TimeDependentAccessRestriction timeDependentAccessRestriction = new TimeDependentAccessRestriction(storage, osm);
        return output -> {
            PrintWriter printWriter = new PrintWriter(output);
            printWriter.println(linkEnterTime);
            printWriter.println();
            final OSMIDParser osmidParser = OSMIDParser.fromEncodingManager(storage.getEncodingManager());
            final BooleanEncodedValue property = storage.getEncodingManager().getBooleanEncodedValue("conditional");
            AllEdgesIterator allEdges = storage.getAllEdges();
            long prevOsmId = -1;
            while (allEdges.next()) {
                if (allEdges.get(property)) {
                    long osmid = osmidParser.getOSMID(allEdges.getFlags());
                    if (osmid != prevOsmId) {
                        final ZonedDateTime zonedDateTime = linkEnterTime.atZone(timeDependentAccessRestriction.zoneId);
                        Way way = osm.ways.get(osmid);
                        ReaderWay readerWay = timeDependentAccessRestriction.readerWay(osmid, way);
                        List<TimeDependentAccessRestriction.ConditionalTagData> timeDependentAccessConditions = TimeDependentAccessRestriction.getTimeDependentAccessConditions(readerWay);
                        if (!timeDependentAccessConditions.isEmpty()) {
                            printWriter.printf("%d\n", osmid);
                            for (TimeDependentAccessRestriction.ConditionalTagData conditionalTagData : timeDependentAccessConditions) {
                                printWriter.println(" "+conditionalTagData.tag);
                                for (TimeDependentAccessRestriction.TimeDependentRestrictionData timeDependentRestrictionData : conditionalTagData.restrictionData) {
                                    printWriter.println("  "+timeDependentRestrictionData.restriction);
                                    for (Rule rule : timeDependentRestrictionData.rules) {
                                        printWriter.println("   " + rule + (timeDependentAccessRestriction.matches(zonedDateTime, rule) ? " <===" : ""));
                                    }
                                }
                            }
                        }
                    }
                    prevOsmId = osmid;
                }
            }
            printWriter.flush();
        };
    }

}
