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
import com.graphhopper.application.util.GraphHopperServerTestConfiguration;
import com.graphhopper.config.Profile;
import com.graphhopper.resources.PtIsochroneResource;
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

import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(DropwizardExtensionsSupport.class)
public class PtIsochroneTest {

    private static final String GRAPH_LOC = "target/PtIsochroneResourceTest";
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private final GeometryFactory geometryFactory = new GeometryFactory();
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration()
                .putObject("graph.vehicles", "foot")
                .putObject("graph.location", GRAPH_LOC)
                .putObject("gtfs.file", "../reader-gtfs/files/sample-feed")
                .putObject("import.osm.ignored_highways", "").
                setProfiles(Collections.singletonList(new Profile("foot").setCustomModel(Helper.createBaseModel("foot"))));
        Helper.removeDir(new File(GRAPH_LOC));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(GRAPH_LOC));
    }

    @Test
    public void testIsoline() {
        WebTarget webTarget = clientTarget(app, "/isochrone")
                .queryParam("vehicle", "pt")
                .queryParam("point", "36.914893,-116.76821") // NADAV
                .queryParam("pt.earliest_departure_time", LocalDateTime.of(2007, 1, 1, 0, 0, 0).atZone(zoneId).toInstant())
                .queryParam("time_limit", 6 * 60 * 60 + 49 * 60); // exactly the time I should arrive at NANAA
        Invocation.Builder request = webTarget.request();
        PtIsochroneResource.Response isochroneResponse = request.get(PtIsochroneResource.Response.class);
        Geometry isoline = isochroneResponse.polygons.get(0).getGeometry();
        // NADAV is in
        assertTrue(isoline.covers(geometryFactory.createPoint(makePrecise(new Coordinate(-116.76821, 36.914893)))));
        // NANAA is in
        assertTrue(isoline.covers(geometryFactory.createPoint(makePrecise(new Coordinate(-116.761472, 36.914944)))));
        // DADAN is in
        assertTrue(isoline.covers(geometryFactory.createPoint(makePrecise(new Coordinate(-116.768242, 36.909489)))));
        // EMSI is in
        assertTrue(isoline.covers(geometryFactory.createPoint(makePrecise(new Coordinate(-116.76218, 36.905697)))));
        // STAGECOACH is out
        assertFalse(isoline.covers(geometryFactory.createPoint(makePrecise(new Coordinate(-116.751677, 36.915682)))));
    }

    @Test
    public void testIsolineFromStation() {
        WebTarget webTarget = clientTarget(app, "/isochrone")
                .queryParam("vehicle", "pt")
                .queryParam("point", "Stop(NADAV)")
                .queryParam("pt.earliest_departure_time", LocalDateTime.of(2007, 1, 1, 0, 0, 0).atZone(zoneId).toInstant())
                .queryParam("time_limit", 6 * 60 * 60 + 49 * 60); // exactly the time I should arrive at NANAA
        Invocation.Builder request = webTarget.request();
        PtIsochroneResource.Response isochroneResponse = request.get(PtIsochroneResource.Response.class);
        Geometry isoline = isochroneResponse.polygons.get(0).getGeometry();
        // NADAV is in
        assertTrue(isoline.covers(geometryFactory.createPoint(makePrecise(new Coordinate(-116.76821, 36.914893)))));
        // NANAA is in
        assertTrue(isoline.covers(geometryFactory.createPoint(makePrecise(new Coordinate(-116.761472, 36.914944)))));
        // DADAN is in
        assertTrue(isoline.covers(geometryFactory.createPoint(makePrecise(new Coordinate(-116.768242, 36.909489)))));
        // EMSI is in
        assertTrue(isoline.covers(geometryFactory.createPoint(makePrecise(new Coordinate(-116.76218, 36.905697)))));
        // STAGECOACH is out
        assertFalse(isoline.covers(geometryFactory.createPoint(makePrecise(new Coordinate(-116.751677, 36.915682)))));
    }

    // Snap coordinate to GraphHopper's implicit grid of allowable points.
    // Otherwise, we can't reliably use coordinates from input data in tests.
    private Coordinate makePrecise(Coordinate coordinate) {
        return new Coordinate(Helper.intToDegree(Helper.degreeToInt(coordinate.x)), Helper.intToDegree(Helper.degreeToInt(coordinate.y)));
    }

}
