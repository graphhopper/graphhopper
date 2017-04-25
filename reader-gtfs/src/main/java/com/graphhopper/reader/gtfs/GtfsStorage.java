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
import com.conveyal.gtfs.model.Fare;
import com.google.transit.realtime.GtfsRealtime;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
import org.mapdb.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipFile;

public class GtfsStorage implements GraphExtension {

	private boolean isClosed = false;
	private Directory dir;
	private Set<String> gtfsFeedIds;
	private Map<String, GTFSFeed> gtfsFeeds = new HashMap<>();
	private HTreeMap<BitSet, Integer> operatingDayPatterns;
	private Map<Integer, BitSet> validities;
	private Atomic.Var<LocalDate> startDate;
	private Map<Integer, String> extra;
	private Map<Integer, Integer> stopSequences;
	private Map<String, Fare> fares;
	private Map<GtfsRealtime.TripDescriptor, int[]> boardEdgesForTrip;
	private Map<GtfsRealtime.TripDescriptor, int[]> leaveEdgesForTrip;

	enum EdgeType {
		HIGHWAY, ENTER_TIME_EXPANDED_NETWORK, LEAVE_TIME_EXPANDED_NETWORK, ENTER_PT, EXIT_PT, HOP, DWELL, BOARD, ALIGHT, OVERNIGHT, TRANSFER, WAIT
    }

	private DB data;

	@Override
	public boolean isRequireNodeField() {
		return true;
	}

	@Override
	public boolean isRequireEdgeField() {
		return false;
	}

	@Override
	public int getDefaultNodeFieldValue() {
		return 0;
	}

	@Override
	public int getDefaultEdgeFieldValue() {
		return EdgeType.HIGHWAY.ordinal();
	}

	@Override
	public void init(Graph graph, Directory dir) {
		this.dir = dir;
	}

	@Override
	public void setSegmentSize(int bytes) {

	}

	@Override
	public GraphExtension copyTo(GraphExtension extStorage) {
		throw new UnsupportedOperationException("copyTo not yet supported");
	}

	@Override
	public boolean loadExisting() {
		this.data = DBMaker.newFileDB(new File(dir.getLocation() + "/transit_schedule")).transactionDisable().mmapFileEnable().readOnly().make();
		init();
		for (String gtfsFeedId : this.gtfsFeedIds) {
			try {
				GTFSFeed feed = new GTFSFeed(dir.getLocation() + "/" + gtfsFeedId);
				this.gtfsFeeds.put(gtfsFeedId, feed);
			} catch (IOException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
		return true;
	}

	@Override
	public GraphExtension create(long byteCount) {
		final File file = new File(dir.getLocation() + "/transit_schedule");
		try {
			Files.deleteIfExists(file.toPath());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		this.data = DBMaker.newFileDB(file).transactionDisable().mmapFileEnable().asyncWriteEnable().make();
		init();
		return this;
	}

	private void init() {
		this.gtfsFeedIds = data.getHashSet("gtfsFeeds");
		this.operatingDayPatterns = data.getHashMap("validities");
		Map<Integer, BitSet> reverseOperatingDayPatterns = new HashMap<>();
		for (Map.Entry<BitSet, Integer> entry : this.operatingDayPatterns.entrySet()) {
			reverseOperatingDayPatterns.put(entry.getValue(), entry.getKey());
		}
		Bind.mapInverse(this.operatingDayPatterns, reverseOperatingDayPatterns);
		this.validities = Collections.unmodifiableMap(reverseOperatingDayPatterns);
		this.startDate = data.getAtomicVar("startDate");
		this.extra = data.getTreeMap("extra");
		this.stopSequences = data.getTreeMap("stopSequences");
		this.fares = data.getTreeMap("fares");
		this.boardEdgesForTrip = data.getHashMap("boardEdgesForTrip");
		this.leaveEdgesForTrip = data.getHashMap("leaveEdgesForTrip");
	}

	void loadGtfsFromFile(String id, ZipFile zip) {
		try {
			GTFSFeed feed = new GTFSFeed(dir.getLocation() + "/" + id);
			feed.loadFromFile(zip);
			this.gtfsFeeds.put(id, feed);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		this.gtfsFeedIds.add(id);
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
		if (!isClosed) {
			isClosed = true;
			data.close();
			for (GTFSFeed feed : gtfsFeeds.values()) {
				feed.close();
			}
		}
	}

	@Override
	public boolean isClosed() {
		return isClosed;
	}

	@Override
	public long getCapacity() {
		return 0;
	}

	void setStartDate(LocalDate startDate) {
		this.startDate.set(startDate);
	}

	LocalDate getStartDate() {
		return startDate.get();
	}

    Map<BitSet, Integer> getOperatingDayPatterns() {
        return operatingDayPatterns;
    }

	Map<Integer, BitSet> getValidities() {
		return validities;
	}

	Map<Integer, String> getExtraStrings() {
		return extra;
	}

	Map<Integer, Integer> getStopSequences() {
		return stopSequences;
	}

	Map<GtfsRealtime.TripDescriptor, int[]> getBoardEdgesForTrip() {
		return boardEdgesForTrip;
	}

	Map<GtfsRealtime.TripDescriptor, int[]> getAlightEdgesForTrip() {
		return leaveEdgesForTrip;
	}

	Map<String, Fare> getFares() {
		return fares;
	}

	Map<String, GTFSFeed> getGtfsFeeds() {
		return Collections.unmodifiableMap(gtfsFeeds);
	}

}
