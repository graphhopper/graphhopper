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
import com.graphhopper.routing.util.spatialrules.AbstractSpatialRule;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Collections;

import static com.graphhopper.http.util.TestUtils.clientTarget;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(DropwizardExtensionsSupport.class)
public class CustomSpatialRulesTest {
    private static final String DIR = "./target/custom-spatial-rules-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        Polygon world = (Polygon) new GeometryFactory().toGeometry(new Envelope(-180, 180, -90, 90));
        SpatialRule customRule = new AbstractSpatialRule(Collections.singletonList(world)) {
            
            @Override
            public String getId() {
                return "custom";
            }
        };
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "car").
                putObject("graph.encoded_values", "spatial_rule,road_environment,road_class,road_access,max_speed").
                putObject("datareader.file", "../core/files/north-bayreuth.osm.gz").
                putObject("graph.location", DIR).
                setSpatialRules(Collections.singletonList(customRule)).
                setProfiles(Collections.singletonList(new Profile("profile").setVehicle("car").setWeighting("fastest")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testCustomRuleIdDetails() {
        final Response response = clientTarget(app, "route?profile=profile&"
                + "point=49.995933,11.54809&point=50.004871,11.517191&details=spatial_rule").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertFalse(json.get("info").has("errors"));
        JsonNode details = json.get("paths").get(0).get("details");
        assertTrue(details.has("spatial_rule"));
        JsonNode spatialRule = details.get("spatial_rule").get(0);
        assertEquals("custom", spatialRule.get(2).asText());
    }

}
