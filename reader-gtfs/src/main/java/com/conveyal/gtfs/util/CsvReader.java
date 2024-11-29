package com.conveyal.gtfs.util;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import com.opencsv.validators.LineValidator;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

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
                .withLineValidator(new SkipEmptyLines())
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
            currenRecord = this.reader.readNext();
            return currenRecord != null;
        } catch (Exception e) {
            return false;
        }
    }

    static class SkipEmptyLines implements LineValidator {
        @Override
        public boolean isValid(String s) {
            return StringUtils.isNotEmpty(s);
        }

        @Override
        public void validate(String s) throws CsvValidationException {
            if (!isValid(s)) {
                throw new CsvValidationException();
            }
        }
    }
}
