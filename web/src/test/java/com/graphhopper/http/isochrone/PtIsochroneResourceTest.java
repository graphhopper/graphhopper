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

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Stop;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.graphhopper.http.GHPointConverterProvider;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.json.geo.JsonFeatureCollection;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static junit.framework.TestCase.assertTrue;

public class PtIsochroneResourceTest {

    private static final String GRAPH_LOC = "target/PtIsochroneResourceTest";
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static GraphHopperStorage graphHopperStorage;
    private static LocationIndex locationIndex;
    private static GtfsStorage gtfsStorage;
    private static PtIsochroneResource isochroneResource;
    private GeometryFactory geometryFactory = new GeometryFactory();

    static {
        Helper.removeDir(new File(GRAPH_LOC));
        final PtFlagEncoder ptFlagEncoder = new PtFlagEncoder();
        final CarFlagEncoder carFlagEncoder = new CarFlagEncoder();
        final FootFlagEncoder footFlagEncoder = new FootFlagEncoder();

        EncodingManager encodingManager = new EncodingManager(Arrays.asList(carFlagEncoder, footFlagEncoder, ptFlagEncoder), 8);
        GHDirectory directory = GraphHopperGtfs.createGHDirectory(GRAPH_LOC);
        gtfsStorage = GraphHopperGtfs.createGtfsStorage();
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
    public void testIsoline() throws JsonProcessingException {
        WebTarget webTarget = resources
                .target("/isochrone")
                .queryParam("point", "36.914893,-116.76821")
                .queryParam("pt.earliest_departure_time", LocalDateTime.of(2007, 1, 1, 0, 0, 0).atZone(zoneId).toInstant())
                .queryParam("time_limit", 6 * 60 * 60 + 49 * 60)
                .queryParam("result", "multipoint");
        Invocation.Builder request = webTarget.request();
        PtIsochroneResource.Response isochroneResponse = request.get(PtIsochroneResource.Response.class);
        JsonFeature feature1 = isochroneResponse.polygons.get(0);
        Geometry isoline = feature1.getGeometry();
        System.out.println(Jackson.newObjectMapper().writeValueAsString(feature1));
//        assertTrue(isoline.contains(geometryFactory.createPoint(new Coordinate(-116.761472, 36.914944))));

        WebTarget webTarget2 = resources
                .target("/isochrone")
                .queryParam("point", "36.914893,-116.76821")
                .queryParam("pt.earliest_departure_time", LocalDateTime.of(2007, 1, 1, 0, 0, 0).atZone(zoneId).toInstant())
                .queryParam("time_limit", 6 * 60 * 60 + 49 * 60);
//                .queryParam("result", "multipoint");
        Invocation.Builder request2 = webTarget2.request();
        PtIsochroneResource.Response isochroneResponse2 = request2.get(PtIsochroneResource.Response.class);

        JsonFeature feature2 = isochroneResponse2.polygons.get(0);
        System.out.println(Jackson.newObjectMapper().writeValueAsString(feature2));

        JsonFeatureCollection jsonFeatureCollection = new JsonFeatureCollection();
        jsonFeatureCollection.getFeatures().add(feature1);
        jsonFeatureCollection.getFeatures().add(feature2);

//        GTFSFeed feed = gtfsStorage.getGtfsFeeds().values().iterator().next();
//        for (Stop stop : feed.stops.values()) {
//            JsonFeature stopFeature = new JsonFeature();
//            stopFeature.setType("Feature");
//            stopFeature.setGeometry(geometryFactory.createPoint(new Coordinate(stop.stop_lon, stop.stop_lat)));
//            HashMap<String, Object> properties = new HashMap<>();
//            properties.put("stop_id", stop.stop_id);
//            stopFeature.setProperties(properties);
//            jsonFeatureCollection.getFeatures().add(stopFeature);
//        }

        System.out.println(Jackson.newObjectMapper().writeValueAsString(jsonFeatureCollection));

    }

    @AfterClass
    public static void close() {
        graphHopperStorage.close();
        locationIndex.close();
    }

}
