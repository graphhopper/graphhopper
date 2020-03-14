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
package com.graphhopper.http.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.config.CHProfileConfig;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.util.GraphHopperServerTestConfiguration;
import static com.graphhopper.http.util.TestUtils.clientTarget;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Peter Karich
 */
public class RouteResourceIssue1574Test {
    private static final String DIR = "./target/andorra-1574-gh/";

    private static final GraphHopperServerTestConfiguration config = new GraphHopperServerTestConfiguration();

    static {
        // this is the reason we put this test into an extra file: we can only reproduce the bug of issue 1574 by increasing the one-way-network size
        config.getGraphHopperConfiguration().
                put("graph.flag_encoders", "car").
                put("prepare.min_network_size", 0).
                put("prepare.min_one_way_network_size", 12).
                put("datareader.file", "../core/files/andorra.osm.pbf").
                put("graph.location", DIR)
                .setProfiles(Collections.singletonList(
                        new ProfileConfig("car_profile").setVehicle("car").setWeighting("fastest")
                ))
                .setCHProfiles(Collections.singletonList(
                        new CHProfileConfig("car_profile")
                ));
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerTestConfiguration> app = new DropwizardAppRule(GraphHopperApplication.class, config);

    @BeforeClass
    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testStallOnDemandBug_issue1574() {
        final Response response = clientTarget(app, "/route?point=42.486984,1.493152&point=42.481863,1.491297&point=42.49697,1.501265&&vehicle=car&weighting=fastest&stall_on_demand=true").request().buildGet().invoke();
        JsonNode json = response.readEntity(JsonNode.class);
        assertFalse("there should be no error, but: " + json.get("message"), json.has("message"));
        System.out.println(json);
        assertEquals(200, response.getStatus());
    }

}
