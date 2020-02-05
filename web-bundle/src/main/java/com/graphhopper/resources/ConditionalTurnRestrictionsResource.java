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

import com.conveyal.osmlib.*;
import com.graphhopper.TimeDependentAccessRestriction;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.view.ConditionalRestrictionView;
import com.graphhopper.view.ConditionalRestrictionsView;
import org.locationtech.jts.geom.Coordinate;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("restrictions")
public class ConditionalTurnRestrictionsResource {

    private final OSM osm;
    private final TimeDependentAccessRestriction timeDependentAccessRestriction;

    @Inject
    public ConditionalTurnRestrictionsResource(GraphHopperStorage storage, OSM osm) {
        this.osm = osm;
        timeDependentAccessRestriction = new TimeDependentAccessRestriction(storage, osm);
    }

    @GET
    @Produces("text/html")
    public ConditionalRestrictionsView conditionalRestrictions() {
        return new ConditionalRestrictionsView(timeDependentAccessRestriction, () -> {
            Stream<ConditionalRestrictionView> conditionalRestrictionViewStream = osm.relations.entrySet().stream()
                    .filter(e -> e.getValue().hasTag("type", "restriction"))
                    .flatMap(entry -> {
                        long osmid = entry.getKey();
                        Relation relation = osm.relations.get(osmid);
                        Map<String, Object> tags = TimeDependentAccessRestriction.getTags(relation);
                        List<TimeDependentAccessRestriction.ConditionalTagData> restrictionData = TimeDependentAccessRestriction.getConditionalTagDataWithTimeDependentConditions(tags).stream().filter(c -> !c.restrictionData.isEmpty())
                                .collect(Collectors.toList());
                        if (!restrictionData.isEmpty()) {
                            Optional<Relation.Member> fromM = relation.members.stream().filter(m -> m.role.equals("from")).findFirst();
                            Optional<Relation.Member> toM = relation.members.stream().filter(m -> m.role.equals("to")).findFirst();
                            if (fromM.isPresent() && toM.isPresent()) {
                                ConditionalRestrictionView view = new ConditionalRestrictionView(timeDependentAccessRestriction);
                                view.osmid = osmid;
                                view.tags = tags;
                                Way from = osm.ways.get(fromM.get().id);
                                Way to = osm.ways.get(toM.get().id);
                                Node fromNode = osm.nodes.get(from.nodes[from.nodes.length / 2]);
                                Node toNode = osm.nodes.get(to.nodes[to.nodes.length / 2]);
                                view.from = new Coordinate(fromNode.getLon(), fromNode.getLat());
                                view.to = new Coordinate(toNode.getLon(), toNode.getLat());
                                view.restrictionData = restrictionData;
                                return Stream.of(view);
                            }
                        }
                        return Stream.empty();
                    });
            return conditionalRestrictionViewStream.iterator();
        });
    }

}
