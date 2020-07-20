package com.conveyal.gtfs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipFile;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.mapdb.BTreeMap;
import org.mapdb.Fun;

import com.conveyal.gtfs.model.Entity;
import com.conveyal.gtfs.model.StopTime;

@SuppressWarnings("rawtypes")
public class GTFSFeedTest {

  private static final String GTFS_WITH_MISSING_STOP_TIME_FILE_PATH = "files/sample-feed-with-empty-stop-time.zip";
  private static final String GRAPH_LOC = "target/GTFSFeedTest";

  @BeforeClass
  public static void init() throws IOException {
    deleteGraphFolder();
  }

  @Test
  public void testCleanMissingStopTime() throws IOException {
    // Given
    ZipFile gtfsFile = new ZipFile(GTFS_WITH_MISSING_STOP_TIME_FILE_PATH);
    File dbFile = new File(GRAPH_LOC);
    GTFSFeed feed = new GTFSFeed(dbFile);
    feed.loadFromFile(gtfsFile, null);

    BTreeMap<Fun.Tuple2, StopTime> expectedStopTimes = feed.stop_times;
    removeMissingStopTimes(expectedStopTimes);

    // When
    feed.cleanMissingStopTimes();

    // Then
    Assertions.assertEquals(feed.stop_times, expectedStopTimes);
  }

  private static void deleteGraphFolder() throws IOException {
    File folderToDelete = new File(GRAPH_LOC);
    Files.deleteIfExists(folderToDelete.toPath());
  }

  private void removeMissingStopTimes(BTreeMap<Fun.Tuple2, StopTime> stopTimes) {
    stopTimes.forEach(((key, stopTime) -> {
      if (stopTime.departure_time == Entity.INT_MISSING && stopTime.arrival_time == Entity.INT_MISSING) {
        stopTimes.remove(key);
      }
    }));
  }
}