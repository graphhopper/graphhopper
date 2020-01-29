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

import com.conveyal.osmlib.Node;
import com.conveyal.osmlib.OSM;
import com.conveyal.osmlib.OSMEntity;
import com.conveyal.osmlib.Relation;
import com.graphhopper.TimeDependentAccessRestriction;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.view.ConditionalRestrictionView;
import com.graphhopper.view.ConditionalRestrictionsView;
import org.locationtech.jts.geom.Coordinate;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.HashMap;
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
    public ConditionalRestrictionsView conditionalRelations() {
        return new ConditionalRestrictionsView(timeDependentAccessRestriction, () -> {
            Stream<ConditionalRestrictionView> conditionalRestrictionViewStream = osm.relations.entrySet().stream()
                    .filter(e -> e.getValue().hasTag("type", "restriction"))
                    .flatMap(entry -> {
                        long osmid = entry.getKey();
                        Relation relation = osm.relations.get(osmid);
                        Map<String, Object> tags = new HashMap<>();
                        for (OSMEntity.Tag tag : relation.tags) {
                            tags.put(tag.key, tag.value);
                        }
                        List<TimeDependentAccessRestriction.ConditionalTagData> restrictionData = TimeDependentAccessRestriction.getConditionalTagDataWithTimeDependentConditions(tags).stream().filter(c -> !c.restrictionData.isEmpty())
                                .collect(Collectors.toList());
                        if (!restrictionData.isEmpty()) {
                            Optional<Relation.Member> via = relation.members.stream().filter(m -> m.role.equals("via")).findFirst();
                            if (via.isPresent()) {
                                ConditionalRestrictionView view = new ConditionalRestrictionView(timeDependentAccessRestriction);
                                view.osmid = osmid;
                                view.tags = tags;
                                Node node = osm.nodes.get(via.get().id);
                                view.from = new Coordinate(node.getLon(), node.getLat());
                                view.to = new Coordinate(node.getLon(), node.getLat());
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
