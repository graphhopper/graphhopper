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

package com.graphhopper.reader.gtfs;


import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Transfer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TransfersTest {

    private Transfers transfers;

    @BeforeAll
    public void init() throws IOException {
        GTFSFeed gtfsFeed = new GTFSFeed();
        gtfsFeed.loadFromFile(new ZipFile("files/another-sample-feed.zip"), "");
        transfers = new Transfers(gtfsFeed);
    }

    @Test
    public void testTransfersByFromRouteEvenIfActuallyNotRouteSpecific() {
        List<Transfer> transfersFromStop = transfers.getTransfersFromStop("MUSEUM", "COURT2MUSEUM");
        assertEquals(1, transfersFromStop.size());
        Transfer transfer = transfersFromStop.get(0);
        assertEquals("MUSEUM", transfer.from_stop_id);
        assertEquals("NEXT_TO_MUSEUM", transfer.to_stop_id);
        assertEquals("COURT2MUSEUM", transfer.from_route_id);
        assertEquals("MUSEUM2AIRPORT", transfer.to_route_id);
    }

    @Test
    public void testTransfersByToRouteEvenIfActuallyNotRouteSpecific() {
        List<Transfer> transfersToStop = transfers.getTransfersToStop("NEXT_TO_MUSEUM", "MUSEUM2AIRPORT");
        assertEquals(2, transfersToStop.size());
        Transfer transfer = transfersToStop.get(0);
        assertEquals("MUSEUM", transfer.from_stop_id);
        assertEquals("NEXT_TO_MUSEUM", transfer.to_stop_id);
        assertEquals("COURT2MUSEUM", transfer.from_route_id);
        assertEquals("MUSEUM2AIRPORT", transfer.to_route_id);

        Transfer withinStationTransfer = transfersToStop.get(1);
        assertEquals("NEXT_TO_MUSEUM", withinStationTransfer.from_stop_id);
        assertEquals("NEXT_TO_MUSEUM", withinStationTransfer.to_stop_id);
        assertNull(withinStationTransfer.from_route_id);
        assertNull(withinStationTransfer.to_route_id);
    }

}
