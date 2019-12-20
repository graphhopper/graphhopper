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

import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.resources.PtIsochroneResource;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PtIsochroneTest {

    private static final String GRAPH_LOC = "target/PtIsochroneResourceTest";
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private GeometryFactory geometryFactory = new GeometryFactory();

    private static final GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();

    static {
        config.getGraphHopperConfiguration().merge(new CmdArgs()
        .put("graph.flag_encoders", "foot")
        .put("graph.location", GRAPH_LOC)
        .put("gtfs.file", "../reader-gtfs/files/sample-feed.zip"));
        Helper.removeDir(new File(GRAPH_LOC));
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerConfiguration> app = new DropwizardAppRule<>(GraphHopperApplication.class, config);

    @BeforeClass
    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(GRAPH_LOC));
    }

    @Test
    public void testIsoline() {
        WebTarget webTarget = app.client()
                .target("http://localhost:8080/isochrone")
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

    // Snap coordinate to GraphHopper's implicit grid of allowable points.
    // Otherwise, we can't reliably use coordinates from input data in tests.
    private Coordinate makePrecise(Coordinate coordinate) {
        return new Coordinate(Helper.intToDegree(Helper.degreeToInt(coordinate.x)), Helper.intToDegree(Helper.degreeToInt(coordinate.y)));
    }

}
