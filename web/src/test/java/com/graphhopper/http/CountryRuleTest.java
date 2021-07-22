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

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.config.Profile;
import com.graphhopper.http.util.GraphHopperServerTestConfiguration;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Collections;

import static com.graphhopper.http.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests GraphHopper including spatial rules
 *
 * @author Robin Boldt
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class CountryRuleTest {
    private static final String DIR = "./target/north-bayreuth-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "car").
                putObject("datareader.file", "../core/files/north-bayreuth.osm.gz").
                putObject("custom_areas.directory", "../core/src/main/resources/com/graphhopper/countries").
                putObject("graph.location", DIR).
                setProfiles(Collections.singletonList(new Profile("profile").setVehicle("car").setWeighting("fastest")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void germanyCountryRuleAvoidsTracks() {
        final Response response = clientTarget(app, "route?profile=profile&"
                + "point=50.010373,11.51792&point=50.005146,11.516633").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertFalse(json.get("info").has("errors"));
        double distance = json.get("paths").get(0).get("distance").asDouble();
        // GermanyCountryRule will avoid TRACK roads, so the route won't take the shortcut through the forest. Otherwise
        // it would only be around 1447m long.
        // todo: there should be a way to enable/disable the country rules, even when countries.geojson is included
        //       in the bundle? or maybe even enable/disable single country rules selectively? once this is possible we
        //       should be able to test both cases here. Probably move this test into GraphHopperTest.
        assertTrue(distance > 4000, "distance wasn't correct:" + distance);
        assertTrue(distance < 4500, "distance wasn't correct:" + distance);
    }

}
