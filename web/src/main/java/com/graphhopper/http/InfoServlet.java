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
package com.graphhopper.http;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.util.Constants;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.BBox;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Karich
 */
public class InfoServlet extends GHBaseServlet {
    @Inject
    private GraphHopperStorage storage;
    @Inject
    @Named("hasElevation")
    private boolean hasElevation;

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        BBox bb = storage.getBounds();
        List<Double> list = new ArrayList<>(4);
        list.add(bb.minLon);
        list.add(bb.minLat);
        list.add(bb.maxLon);
        list.add(bb.maxLat);

        final JsonNodeFactory jsonNodeFactory = new JsonNodeFactory(false);
        final ObjectNode json = jsonNodeFactory.objectNode();
        json.putPOJO("bbox", list);

        String[] vehicles = storage.getEncodingManager().toString().split(",");
        json.putPOJO("supported_vehicles", vehicles);
        ObjectNode features = json.putObject("features");
        for (String v : vehicles) {
            ObjectNode perVehicleJson = features.putObject(v);
            perVehicleJson.put("elevation", hasElevation);
        }

        json.put("version", Constants.VERSION);
        json.put("build_date", Constants.BUILD_DATE);

        StorableProperties props = storage.getProperties();
        json.put("import_date", props.get("datareader.import.date"));

        if (!Helper.isEmpty(props.get("datareader.data.date")))
            json.put("data_date", props.get("datareader.data.date"));

        String tmpDate = props.get(Parameters.CH.PREPARE + "date");
        if (!Helper.isEmpty(tmpDate)) {
            json.put("prepare_ch_date", tmpDate);
            json.put("prepare_date", tmpDate);
        }

        writeJson(req, res, json);
    }
}
