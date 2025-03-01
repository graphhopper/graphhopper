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

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Location;
import com.conveyal.gtfs.model.Stop;
import com.graphhopper.gtfs.*;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.TranslationMap;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.graphhopper.gtfs.GtfsHelper.time;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class FlexIT {

    private static final String GRAPH_LOC = "target/GraphHopperFlexIT";
    private static PtRouter ptRouter;
    private static final ZoneId zoneId = ZoneId.of("America/Chicago");
    private static GraphHopperGtfs graphHopperGtfs;
    private static GTFSFeed feed;

    @BeforeAll
    public static void init() {
        GraphHopperConfig ghConfig = new GraphHopperConfig();
        ghConfig.putObject("graph.location", GRAPH_LOC);
        ghConfig.putObject("import.osm.ignored_highways", "");
        ghConfig.putObject("gtfs.file", "files/on-demand-services-within-a-single-zone");
        ghConfig.putObject("graph.encoded_values", "foot_access, foot_priority, foot_average_speed");
        ghConfig.setProfiles(List.of(TestProfiles.accessSpeedAndPriority("foot")));

        Helper.removeDir(new File(GRAPH_LOC));
        graphHopperGtfs = new GraphHopperGtfs(ghConfig);
        graphHopperGtfs.init(ghConfig);
        graphHopperGtfs.importOrLoad();
        ptRouter = new PtRouterImpl.Factory(ghConfig, new TranslationMap().doImport(), graphHopperGtfs.getBaseGraph(), graphHopperGtfs.getEncodingManager(), graphHopperGtfs.getLocationIndex(), graphHopperGtfs.getGtfsStorage())
                .createWithoutRealtimeFeed();
        feed = graphHopperGtfs.getGtfsStorage().getGtfsFeeds().get("gtfs_0");
    }

    @AfterAll
    public static void close() {
        graphHopperGtfs.close();
    }

    @Test
    public void testGotLocations() {
        assertThat(feed.locations.keySet()).contains("area_708", "area_715");
    }

}
