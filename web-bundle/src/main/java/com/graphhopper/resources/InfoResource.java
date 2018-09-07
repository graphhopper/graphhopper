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

import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.Constants;
import com.graphhopper.util.shapes.BBox;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Peter Karich
 */
@Path("info")
@Produces(MediaType.APPLICATION_JSON)
public class InfoResource {
    private final GraphHopperStorage storage;

    private boolean hasElevation;

    @Inject
    public InfoResource(GraphHopperStorage storage, @Named("hasElevation") Boolean hasElevation) {
        this.storage = storage;
        this.hasElevation = hasElevation;
    }

    public static class Info {
        public static class PerVehicle {
            public boolean elevation;
        }

        public BBox bbox;
        public String[] supported_vehicles;
        public final Map<String, PerVehicle> features = new HashMap<>();
        public String version = Constants.VERSION;
        public String build_date = Constants.BUILD_DATE;
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
        info.supported_vehicles = storage.getEncodingManager().toString().split(",");
        for (String v : info.supported_vehicles) {
            Info.PerVehicle perVehicleJson = new Info.PerVehicle();
            perVehicleJson.elevation = hasElevation;
            info.features.put(v, perVehicleJson);
        }
        info.import_date = storage.getProperties().get("datareader.import.date");
        info.data_date = storage.getProperties().get("datareader.data.date");
        info.prepare_ch_date = storage.getProperties().get("prepare.ch.date");
        info.prepare_date = storage.getProperties().get("prepare.ch.date");
        return info;
    }
}
