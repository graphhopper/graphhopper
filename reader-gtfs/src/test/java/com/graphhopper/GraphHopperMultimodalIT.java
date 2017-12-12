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

package com.graphhopper;

import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.reader.gtfs.GtfsStorage;
import com.graphhopper.reader.gtfs.PtFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class GraphHopperMultimodalIT {

    private static final String GRAPH_LOC = "target/GraphHopperMultimodalIT";
    private static GraphHopperGtfs graphHopper;
    private static final ZoneId zoneId = ZoneId.of("America/Los_Angeles");
    private static GraphHopperStorage graphHopperStorage;
    private static LocationIndex locationIndex;

    @BeforeClass
    public static void init() {
        Helper.removeDir(new File(GRAPH_LOC));
        final PtFlagEncoder ptFlagEncoder = new PtFlagEncoder();
        EncodingManager encodingManager = new EncodingManager(Arrays.asList(ptFlagEncoder), 8);
        GHDirectory directory = GraphHopperGtfs.createGHDirectory(GRAPH_LOC);
        GtfsStorage gtfsStorage = GraphHopperGtfs.createGtfsStorage();
        graphHopperStorage = GraphHopperGtfs.createOrLoad(directory, encodingManager, ptFlagEncoder, gtfsStorage, false, Collections.singleton("files/sample-feed.zip"), Collections.singleton("files/beatty.osm"));
        locationIndex = GraphHopperGtfs.createOrLoadIndex(directory, graphHopperStorage, ptFlagEncoder);
        graphHopper = GraphHopperGtfs.createFactory(ptFlagEncoder, GraphHopperGtfs.createTranslationMap(), graphHopperStorage, locationIndex, gtfsStorage)
                .createWithoutRealtimeFeed();
    }

    @AfterClass
    public static void close() {
        graphHopperStorage.close();
        locationIndex.close();
    }

    @Test
    public void testDepartureTimeOfAccessLeg() {
        GHRequest ghRequest = new GHRequest(
                36.91311729030539,-116.76769495010377,
                36.91260259593356,-116.76149368286134
        );
        ghRequest.getHints().put(Parameters.PT.EARLIEST_DEPARTURE_TIME, LocalDateTime.of(2007,1,1,6,40,0).atZone(zoneId).toInstant());
        ghRequest.getHints().put(Parameters.PT.PROFILE_QUERY, true);

        GHResponse response = graphHopper.route(ghRequest);

        assertThat(response.getAll().get(0).getLegs().get(0).getDepartureTime().toInstant().atZone(zoneId).toLocalTime())
                .isEqualTo("06:41:06");
        assertThat(response.getAll().get(0).getLegs().get(0).getArrivalTime().toInstant())
                .isEqualTo(response.getAll().get(0).getLegs().get(1).getDepartureTime().toInstant());
    }

}
