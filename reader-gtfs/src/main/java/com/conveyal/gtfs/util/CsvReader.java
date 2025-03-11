package com.conveyal.gtfs.util;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * A supplement wrapper for the old javacsv based implementation.
 */
public class CsvReader {

    private final CSVReader reader;
    private final Map<String, Integer> headerIndex = new HashMap<>();

    private String[] currenRecord;

    public CsvReader(InputStream inputStream, char delimiter, Charset charset) {
        this(new InputStreamReader(inputStream, charset), delimiter);
    }

    public CsvReader(Reader reader) {
        this(reader, ',');
    }

    public CsvReader(Reader reader, char delimiter) {
        this.reader = new CSVReaderBuilder(reader)
                .withCSVParser(new CSVParserBuilder().withSeparator(delimiter).build())
                .build();
    }

    public void readHeaders() {
        if (currenRecord != null) {
            throw new IllegalStateException("Reader has already been used to load data rows.");
        }
        if (readRecord()) {
            setHeaders(currenRecord);
        }
    }

    public void setHeaders(String[] strings) {
        int i = 0;
        for (String entry : strings) {
            headerIndex.put(entry, i++);
        }
    }

    public String get(String column) {
        if (headerIndex.isEmpty()) {
            throw new IllegalArgumentException("Unknown header.");
        }
        final Integer index = headerIndex.get(column);
        if (index == null) {
            return null;
        }
        return index >= currenRecord.length ? null : currenRecord[index];
    }

    public boolean readRecord() {
        try {
            String[] nextRecord;
            do {
                if (this.reader.peek() == null) {
                    currenRecord = null;
                    return false;
                }
                nextRecord = this.reader.readNextSilently();
            } while (isEmptyLine(nextRecord));

            currenRecord = nextRecord;
            return true;
        } catch (IOException e) {
            currenRecord = null;
            return false;
        }
    }

    /*
     * Checks if the provided record was produced by an empty line.
     */
    private boolean isEmptyLine(String[] record) {
        // an empty line results in a non-empty record consisting of one empty string: [""]
        return record.length == 1 && (record[0] == null || record[0].isEmpty());
    }

}
