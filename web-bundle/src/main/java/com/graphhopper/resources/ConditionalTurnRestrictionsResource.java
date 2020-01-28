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
import com.conveyal.osmlib.Node;
import com.conveyal.osmlib.OSM;
import com.conveyal.osmlib.OSMEntity;
import com.conveyal.osmlib.Relation;
import com.graphhopper.TimeDependentAccessRestriction;
import com.graphhopper.storage.GraphHopperStorage;
import io.dropwizard.views.View;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("restrictions")
public class ConditionalTurnRestrictionsResource {

    private final OSM osm;
    private final GraphHopperStorage storage;

    @Inject
    public ConditionalTurnRestrictionsResource(GraphHopperStorage storage, OSM osm) {
        this.storage = storage;
        this.osm = osm;
    }

    @GET
    @Produces("text/html")
    public ConditionalTurnRestrictionsView conditionalRelations() {
        return new ConditionalTurnRestrictionsView();
    }

    public class ConditionalTurnRestrictionsView extends View {

        public final List<ConditionalTurnRestrictionView> restrictions = new ArrayList<>();
        private final Instant linkEnterTime;
        private final TimeDependentAccessRestriction timeDependentAccessRestriction;

        protected ConditionalTurnRestrictionsView() {
            super("/assets/wurst.ftl");
            linkEnterTime = Instant.now();
            timeDependentAccessRestriction = new TimeDependentAccessRestriction(storage, osm);
        }

        public Iterable<ConditionalTurnRestrictionView> getRestrictions() {
            return () -> osm.relations.entrySet().stream()
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
                                ConditionalTurnRestrictionView view = new ConditionalTurnRestrictionView();
                                view.osmid = osmid;
                                view.node = osm.nodes.get(via.get().id);
                                view.restrictionData = restrictionData;
                                return Stream.of(view);
                            }
                        }
                        return Stream.empty();
                    }).iterator();
        }

        public boolean matches(Rule rule) {
            return timeDependentAccessRestriction.matches(linkEnterTime.atZone(timeDependentAccessRestriction.zoneId), rule);
        }

        public class ConditionalTurnRestrictionView {
            public Node getNode() {
                return node;
            }

            public long getOsmid() {
                return osmid;
            }

            public long osmid;
            public Node node;

            public List<TimeDependentAccessRestriction.ConditionalTagData> getRestrictionData() {
                return restrictionData;
            }

            public List<TimeDependentAccessRestriction.ConditionalTagData> restrictionData;
            public final ZonedDateTime zonedDateTime;

            public ConditionalTurnRestrictionView() {
                zonedDateTime = linkEnterTime.atZone(timeDependentAccessRestriction.zoneId);
            }
        }

    }

}
