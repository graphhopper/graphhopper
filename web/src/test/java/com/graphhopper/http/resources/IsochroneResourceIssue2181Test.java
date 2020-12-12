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
import com.graphhopper.config.Profile;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.http.util.GraphHopperServerTestConfiguration;
import com.graphhopper.json.geo.JsonFeatureCollection;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Arrays;

import static com.graphhopper.http.util.TestUtils.clientTarget;
import static com.graphhopper.util.Parameters.Routing.BLOCK_AREA;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
public class IsochroneResourceIssue2181Test {
    private static final String DIR = "./target/issue-2181-gh/";
    public static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "car|turn_costs=true").
                putObject("datareader.file", "../core/files/issue-2181.osm.gz").
                putObject("graph.location", DIR).
                putObject("prepare.min_network_size", 0). // We want those encroaching nodes
                setProfiles(Arrays.asList(
                        new Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(true)
                ));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    private final GeometryFactory geometryFactory = new GeometryFactory();


    @Test
    public void nonLocalUnreachablePointsAreExcludedIssue2181() {
        Response response = clientTarget(app, "/isochrone?profile=car&point=54.59485,24.02584&distance_limit=5000&type=geojson")
                .request().buildGet().invoke();
        JsonFeatureCollection collection = response.readEntity(JsonFeatureCollection.class);
        Polygon polygon = (Polygon) collection.getFeatures().get(0).getGeometry();
        assertTrue(polygon.contains(geometryFactory.createPoint(new Coordinate(24.02584, 54.59485))));
        assertFalse(polygon.contains(geometryFactory.createPoint(new Coordinate(24.0438, 54.59706))));
    }
}
