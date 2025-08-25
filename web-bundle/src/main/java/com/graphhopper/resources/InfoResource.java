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

import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.util.Constants;
import org.locationtech.jts.geom.Envelope;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.*;

/**
 * @author Peter Karich
 */
@Path("info")
@Produces(MediaType.APPLICATION_JSON)
public class InfoResource {

    private final GraphHopperConfig config;
    private final BaseGraph baseGraph;
    private final EncodingManager encodingManager;
    private final StorableProperties properties;
    private final boolean hasElevation;
    private final Set<String> privateEV;

    @Inject
    public InfoResource(GraphHopperConfig config, GraphHopper graphHopper, @Named("hasElevation") Boolean hasElevation) {
        this.config = config;
        this.encodingManager = graphHopper.getEncodingManager();
        this.privateEV = new HashSet<>(Arrays.asList(config.getString("graph.encoded_values.private", "").split(",")));
        for (String pEV : privateEV) {
            if (!pEV.isEmpty() && !encodingManager.hasEncodedValue(pEV))
                throw new IllegalArgumentException("A private encoded value does not exist.");
        }
        this.baseGraph = graphHopper.getBaseGraph();
        this.properties = graphHopper.getProperties();
        this.hasElevation = hasElevation;
    }

    public static class Info {
        public static class ProfileData {
            // for deserialization in e.g. tests
            public ProfileData() {
            }

            public ProfileData(String name) {
                this.name = name;
            }

            public String name;
        }

        public Envelope bbox;
        public final List<ProfileData> profiles = new ArrayList<>();
        public String version = Constants.VERSION;
        public boolean elevation;
        public Map<String, List<Object>> encoded_values;
        public String import_date;
        public String data_date;
    }

    @GET
    public Info getInfo() {
        final Info info = new Info();
        info.bbox = new Envelope(baseGraph.getBounds().minLon, baseGraph.getBounds().maxLon, baseGraph.getBounds().minLat, baseGraph.getBounds().maxLat);
        for (Profile p : config.getProfiles()) {
            Info.ProfileData profileData = new Info.ProfileData(p.getName());
            info.profiles.add(profileData);
        }
        if (config.has("gtfs.file"))
            info.profiles.add(new Info.ProfileData("pt"));

        info.elevation = hasElevation;
        info.import_date = properties.get("datareader.import.date");
        info.data_date = properties.get("datareader.data.date");

        List<EncodedValue> evList = encodingManager.getEncodedValues();
        info.encoded_values = new LinkedHashMap<>();
        for (EncodedValue encodedValue : evList) {
            List<Object> possibleValueList = new ArrayList<>();
            String name = encodedValue.getName();
            if (privateEV.contains(name)) {
                continue;
            } else if (encodedValue instanceof EnumEncodedValue) {
                for (Enum o : ((EnumEncodedValue) encodedValue).getValues()) {
                    possibleValueList.add(o.name());
                }
            } else if (encodedValue instanceof BooleanEncodedValue) {
                possibleValueList.add("true");
                possibleValueList.add("false");
            } else if (encodedValue instanceof DecimalEncodedValue || encodedValue instanceof IntEncodedValue) {
                possibleValueList.add(">number");
                possibleValueList.add("<number");
            } else {
                // we only add enum, boolean and numeric encoded values to the list
                continue;
            }
            info.encoded_values.put(name, possibleValueList);
        }
        return info;
    }
}
