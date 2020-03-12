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
import com.graphhopper.routing.profiles.EncodedValueFactory;
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
    private final EncodedValueFactory evFactory;
    private final boolean hasElevation;

    @Inject
    public InfoResource(GraphHopperConfig config, GraphHopper graphHopper, @Named("hasElevation") Boolean hasElevation) {
        this.config = config;
        this.evFactory = graphHopper.getEncodedValueFactory();
        this.storage = graphHopper.getGraphHopperStorage();
        this.hasElevation = hasElevation;
    }

    public static class Info {
        public static class PerVehicle {
            public boolean elevation;
            public boolean turn_costs;
        }

        public BBox bbox;
        public List<String> supported_vehicles;
        public Map<String, List<Object>> encoded_values;
        public final Map<String, PerVehicle> features = new HashMap<>();
        public String version = Constants.VERSION;
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
        List<String> encoderNames = Arrays.asList(storage.getEncodingManager().toString().split(","));
        info.supported_vehicles = new ArrayList<>(encoderNames);
        if (config.has("gtfs.file")) {
            info.supported_vehicles.add("pt");
        }
        for (String encoderName : encoderNames) {
            Info.PerVehicle perVehicleJson = new Info.PerVehicle();
            perVehicleJson.elevation = hasElevation;
            perVehicleJson.turn_costs = storage.getEncodingManager().getEncoder(encoderName).supportsTurnCosts();
            info.features.put(encoderName, perVehicleJson);
        }
        if (config.has("gtfs.file")) {
            info.features.put("pt", new InfoResource.Info.PerVehicle());
        }
        info.import_date = storage.getProperties().get("datareader.import.date");
        info.data_date = storage.getProperties().get("datareader.data.date");
        info.prepare_ch_date = storage.getProperties().get("prepare.ch.date");
        info.prepare_date = storage.getProperties().get("prepare.ch.date");

        // do not list all supported encoded values like the none-shared ones or *.turn_costs or max_speed (not possible within the value map)
        List<String> ev = Arrays.asList("country", "get_off_bike", "hazmat", "hazmat_tunnel", "hazmat_water",
                "road_access", "road_class", "road_class_link", "road_environment", "roundabout",
                "bike_network", "foot_network", "surface", "toll", "track_type");
        info.encoded_values = new LinkedHashMap<>();
        for (String encodedValue : ev) {
            if (!storage.getEncodingManager().hasEncodedValue(encodedValue))
                continue;

            List<Object> possibleValueList = new ArrayList<>();
            try {
                Class<? extends Enum> enumClass = evFactory.findValues(encodedValue);
                for (Object o : enumClass.getEnumConstants()) {
                    possibleValueList.add(o.toString());
                }
            } catch (IllegalArgumentException ex) {
                // we expect BooleanEncodedValue here
                possibleValueList.add(true);
                possibleValueList.add(false);
            }

            info.encoded_values.put(encodedValue, possibleValueList);
        }
        return info;
    }
}
