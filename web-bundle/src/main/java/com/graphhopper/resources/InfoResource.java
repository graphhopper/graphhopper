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
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.Constants;
import com.graphhopper.util.shapes.BBox;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.*;

/**
 * @author Peter Karich
 */
@Path("info")
@Produces(MediaType.APPLICATION_JSON)
public class InfoResource {

    private final GraphHopperConfig config;
    private final GraphHopperStorage storage;
    private final boolean hasElevation;

    @Inject
    public InfoResource(GraphHopperConfig config, GraphHopper graphHopper, @Named("hasElevation") Boolean hasElevation) {
        this.config = config;
        this.storage = graphHopper.getGraphHopperStorage();
        this.hasElevation = hasElevation;
    }

    public static class Info {
        public static class ProfileData {
            public ProfileData() {
            }

            public ProfileData(String profileName, String vehicle) {
                this.profileName = profileName;
                this.vehicle = vehicle;
            }

            public String profileName;
            public String vehicle;
        }

        public BBox bbox;
        public final List<ProfileData> profiles = new ArrayList<>();
        public List<String> supported_vehicles;
        public String version = Constants.VERSION;
        public boolean elevation;
        public Map<String, List<Object>> encoded_values;
        public String import_date;
        public String data_date;
        public String prepare_ch_date;
        public String prepare_date;
    }

    @GET
    public Info getInfo() {
        final Info info = new Info();
        // use bbox always without elevation (for backward compatibility)
        info.bbox = new BBox(storage.getBounds().minLon, storage.getBounds().maxLon, storage.getBounds().minLat, storage.getBounds().maxLat);
        for (Profile p : config.getProfiles()) {
            Info.ProfileData profileData = new Info.ProfileData(p.getName(), p.getVehicle());
            info.profiles.add(profileData);
        }
        if (config.has("gtfs.file"))
            info.profiles.add(new Info.ProfileData("pt", "pt"));

        info.elevation = hasElevation;
        List<String> encoderNames = Arrays.asList(storage.getEncodingManager().toString().split(","));
        info.supported_vehicles = new ArrayList<>(encoderNames);
        if (config.has("gtfs.file")) {
            info.supported_vehicles.add("pt");
        }
        info.import_date = storage.getProperties().get("datareader.import.date");
        info.data_date = storage.getProperties().get("datareader.data.date");
        info.prepare_ch_date = storage.getProperties().get("prepare.ch.date");
        info.prepare_date = storage.getProperties().get("prepare.ch.date");

        // do not list all supported encoded values like the none-shared ones or *.turn_costs
        List<EncodedValue> evList = storage.getEncodingManager().getAllShared();
        info.encoded_values = new LinkedHashMap<>();
        for (EncodedValue encodedValue : evList) {
            List<Object> possibleValueList = new ArrayList<>();
            if (encodedValue instanceof EnumEncodedValue) {
                for (Object o : ((EnumEncodedValue) encodedValue).getValues()) {
                    possibleValueList.add(o.toString());
                }
            } else if (encodedValue instanceof BooleanEncodedValue) {
                possibleValueList.add("true");
                possibleValueList.add("false");
            } else if (encodedValue instanceof DecimalEncodedValue || encodedValue instanceof IntEncodedValue) {
                possibleValueList.add(">number");
                possibleValueList.add("<number");
            } else {
                // we only add enum encoded values and boolean encoded values to the list of possible values
                continue;
            }
            info.encoded_values.put(encodedValue.getName(), possibleValueList);
        }
        return info;
    }
}
