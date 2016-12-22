package com.graphhopper.suite;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class TripQueryCsvReader {

    private final CsvMapper objectMapper;
    private final CsvSchema schema;
    {
        objectMapper = new CsvMapper();
        objectMapper.registerModule(new JavaTimeModule());
        schema = objectMapper.schemaFor(TripQuery.class).withHeader();
    }

    public List<TripQuery> read(final File file) throws IOException {
        try (InputStream inputStream = new FileInputStream(file)) {
            return read(inputStream);
        }
    }

    public List<TripQuery> read(final InputStream inputStream) throws IOException {
        final List<TripQuery> stopLocationMappingDtos = objectMapper
                        .readerFor(TripQuery.class).with(schema)
                        .<TripQuery>readValues(inputStream).readAll();
        return stopLocationMappingDtos;
    }
}
