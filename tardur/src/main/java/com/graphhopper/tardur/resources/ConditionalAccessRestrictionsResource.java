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

package com.graphhopper.tardur.resources;

import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.tardur.TimeDependentRestrictionsDAO;
import com.graphhopper.tardur.view.ConditionalRestrictionView;
import com.graphhopper.tardur.view.TimeDependentRestrictionsView;
import com.graphhopper.timezone.core.TimeZones;
import org.locationtech.jts.geom.Coordinate;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.graphhopper.graphsupport.GraphSupport.allEdges;

@Path("conditional-access")
public class ConditionalAccessRestrictionsResource {

    private final GraphHopperStorage storage;
    private final TimeZones timeZones;

    @Inject
    public ConditionalAccessRestrictionsResource(GraphHopperStorage storage, TimeZones timeZones) {
        this.storage = storage;
        this.timeZones = timeZones;
    }

    @GET
    @Produces("text/html")
    public TimeDependentRestrictionsView timeDependentAccessRestrictions() {
        TimeDependentRestrictionsDAO timeDependentRestrictionsDAO = new TimeDependentRestrictionsDAO(storage, timeZones);
        return new TimeDependentRestrictionsView(new TimeDependentRestrictionsDAO(storage, timeZones), () -> {
            BooleanEncodedValue property = storage.getEncodingManager().getBooleanEncodedValue("conditional");
            IntEncodedValue tagPointer = storage.getEncodingManager().getIntEncodedValue("edgetagpointer");
            return allEdges(storage)
                    .filter(edge -> edge.get(property))
                    .flatMap(edge -> {
                        int p = edge.get(tagPointer);
                        Map<String, String> tags = storage.getTagStore().getAll(p);
                        List<TimeDependentRestrictionsDAO.ConditionalTagData> timeDependentAccessConditions = TimeDependentRestrictionsDAO.getTimeDependentAccessConditions(tags);
                        if (!timeDependentAccessConditions.isEmpty()) {
                            ConditionalRestrictionView view = new ConditionalRestrictionView(timeDependentRestrictionsDAO, timeZones);
                            view.id = edge.getEdge();
                            view.tags = tags;
                            view.from = new Coordinate(storage.getNodeAccess().getLon(edge.getBaseNode()), storage.getNodeAccess().getLat(edge.getBaseNode()));
                            view.to = new Coordinate(storage.getNodeAccess().getLon(edge.getAdjNode()), storage.getNodeAccess().getLat(edge.getAdjNode()));
                            view.restrictionData = timeDependentAccessConditions;
                            return Stream.of(view);
                        } else {
                            return Stream.empty();
                        }
                    }).iterator();
        });
    }


}
