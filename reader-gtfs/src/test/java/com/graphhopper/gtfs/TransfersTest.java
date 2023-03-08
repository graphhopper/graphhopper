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

package com.graphhopper.gtfs;


import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Transfer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TransfersTest {

    private Transfers sampleFeed;
    private Transfers anotherSampleFeed;

    @BeforeAll
    public void init() throws IOException {
        GTFSFeed gtfsFeed1 = new GTFSFeed();
        gtfsFeed1.loadFromZipfileOrDirectory(new File("files/sample-feed"), "");
        sampleFeed = new Transfers(gtfsFeed1);
        GTFSFeed gtfsFeed2 = new GTFSFeed();
        gtfsFeed2.loadFromZipfileOrDirectory(new File("files/another-sample-feed"), "");
        anotherSampleFeed = new Transfers(gtfsFeed2);
    }

    @Test
    public void testTransfersByToRoute() {
        assertTrue(anotherSampleFeed.hasNoRouteSpecificArrivalTransferRules("MUSEUM"), "Transfer model says we don't have route-dependent arrival platform at from-stop");
        assertTrue(anotherSampleFeed.hasNoRouteSpecificDepartureTransferRules("NEXT_TO_MUSEUM"), "Transfer model says we don't have route-dependent departure platform at to-stop");
        List<Transfer> transfersToStop = anotherSampleFeed.getTransfersToStop("NEXT_TO_MUSEUM", null);
        assertEquals(2, transfersToStop.size());
        Transfer transfer = transfersToStop.get(0);
        assertEquals("MUSEUM", transfer.from_stop_id);
        assertEquals("NEXT_TO_MUSEUM", transfer.to_stop_id);
        Assertions.assertNull(transfer.from_route_id);
        Assertions.assertNull(transfer.to_route_id);
        Assertions.assertEquals(600, transfer.min_transfer_time);

        Transfer withinStationTransfer = transfersToStop.get(1);
        assertEquals("NEXT_TO_MUSEUM", withinStationTransfer.from_stop_id);
        assertEquals("NEXT_TO_MUSEUM", withinStationTransfer.to_stop_id);
        assertNull(withinStationTransfer.from_route_id);
        assertNull(withinStationTransfer.to_route_id);
    }

    @Test
    public void testInternalTransfersByToRouteIfRouteSpecific() {
        List<Transfer> transfersToStop = sampleFeed.getTransfersToStop("BEATTY_AIRPORT", "AB");
        assertEquals(5, transfersToStop.size());
        assertEquals("AB", transfersToStop.get(0).from_route_id);
        assertEquals("FUNNY_BLOCK_AB", transfersToStop.get(1).from_route_id);
        assertEquals("STBA", transfersToStop.get(2).from_route_id);
        assertEquals("AAMV", transfersToStop.get(3).from_route_id);
        assertEquals("ABBFC", transfersToStop.get(4).from_route_id);
    }

}
