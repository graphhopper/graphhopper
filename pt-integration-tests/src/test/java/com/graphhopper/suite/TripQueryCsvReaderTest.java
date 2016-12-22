package com.graphhopper.suite;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TripQueryCsvReaderTest {

    private final TripQueryCsvReader reader = new TripQueryCsvReader();

    @Test
    public void readsTripQueriesFromCsv() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("trips-01.csv")) {
            final List<TripQuery> tripQueries = reader.read(is);
            assertEquals(1, tripQueries.size());
            assertEquals("Mundenheim Nord", tripQueries.get(0).getFromName());
        }
    }

}
