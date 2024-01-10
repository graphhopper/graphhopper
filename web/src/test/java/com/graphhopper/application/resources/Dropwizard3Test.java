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

package com.graphhopper.application.resources;

import com.graphhopper.application.GraphHopperApplication;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.config.Profile;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.Entity;
import java.io.File;
import java.util.Arrays;

@ExtendWith(DropwizardExtensionsSupport.class)
public class Dropwizard3Test {
    private static final String DIR = "./target/dropwizard3-test-gh";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        Helper.removeDir(new File(DIR));
        GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();
        config.getGraphHopperConfiguration()
                .putObject("datareader.file", "../core/files/andorra.osm.pbf")
                .putObject("graph.location", DIR)
                .putObject("import.osm.ignored_highways", "")
                .setProfiles(Arrays.asList(
                        new Profile("car").setVehicle("car")
                ));
        return config;
    }

    @Test
    public void boundsWithFeatureCollection() {
        app.client().target("http://localhost:8080/route/").request().post(Entity.json("{\"profile\": \"car\", \"points\": [[1.536198,42.554851], [1.548128, 42.510071]]}"));
        app.client().target("http://localhost:8080/route/").request().post(Entity.json("{\"profile\": \"car\", \"points\": [[1.536198,42.554851], [1.548128, 42.510071]]}"));
        app.client().target("http://localhost:8080/route/").request().post(Entity.json("{\"profile\": \"car\", \"points\": [[1.536198,42.554851], [1.548128, 42.510071]]}"));
        app.client().target("http://localhost:8080/route/").request().post(Entity.json("{\"profile\": \"car\", \"points\": [[1.536198,42.554851], [1.548128, 42.510071]]}"));
        app.client().target("http://localhost:8080/route/").request().post(Entity.json("{\"profile\": \"car\", \"points\": [[1.536198,42.554851], [1.548128, 42.510071]]}"));
        app.client().target("http://localhost:8080/route/").request().post(Entity.json("{\"profile\": \"car\", \"points\": [[1.536198,42.554851], [1.548128, 42.510071]]}"));
        app.client().target("http://localhost:8080/route/").request().post(Entity.json("{\"profile\": \"car\", \"points\": [[1.536198,42.554851], [1.548128, 42.510071]]}"));
    }
}
