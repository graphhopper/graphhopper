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

package com.graphhopper.http.isochrone;

import com.graphhopper.http.GHPointConverterProvider;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.reader.gtfs.PtFlagEncoder;
import com.graphhopper.resources.PtIsochroneResource;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.junit.AfterClass;
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
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PtIsochroneResourceTest {

    private static final String GRAPH_LOC = "target/PtIsochroneResourceTest";
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static GraphHopperStorage graphHopperStorage;
    private static LocationIndex locationIndex;
    private static PtIsochroneResource isochroneResource;
    private GeometryFactory geometryFactory = new GeometryFactory();

    static {
        Helper.removeDir(new File(GRAPH_LOC));
        final PtFlagEncoder ptFlagEncoder = new PtFlagEncoder();
        final CarFlagEncoder carFlagEncoder = new CarFlagEncoder();
        final FootFlagEncoder footFlagEncoder = new FootFlagEncoder();

        EncodingManager encodingManager = new EncodingManager.Builder(12).add(carFlagEncoder).add(footFlagEncoder).add(ptFlagEncoder).build();
        GHDirectory directory = GraphHopperGtfs.createGHDirectory(GRAPH_LOC);
        GtfsStorage gtfsStorage = GraphHopperGtfs.createGtfsStorage();
        graphHopperStorage = GraphHopperGtfs.createOrLoad(directory, encodingManager, ptFlagEncoder, gtfsStorage, Collections.singleton("../reader-gtfs/files/sample-feed.zip"), Collections.emptyList());
        locationIndex = GraphHopperGtfs.createOrLoadIndex(directory, graphHopperStorage);
        isochroneResource = new PtIsochroneResource(gtfsStorage, graphHopperStorage.getEncodingManager(), graphHopperStorage, locationIndex);
    }

    @ClassRule
    public static final ResourceTestRule resources = ResourceTestRule.builder()
            .addProvider(new GHPointConverterProvider())
            .setMapper(Jackson.newObjectMapper())
            .addResource(isochroneResource)
            .build();


    @Test
    public void testIsoline() {
        WebTarget webTarget = resources
                .target("/isochrone")
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

    @AfterClass
    public static void close() {
        graphHopperStorage.close();
        locationIndex.close();
    }

}
