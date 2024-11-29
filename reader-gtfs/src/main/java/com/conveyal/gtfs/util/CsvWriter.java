package com.conveyal.gtfs.util;

import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class CsvWriter {

    private final ICSVWriter writer;

    private boolean empty = true;
    private final List<String> currentRecord = new ArrayList<>();

    public CsvWriter(OutputStream outputStream, char delimiter, Charset charset) {
        writer = new CSVWriterBuilder(new OutputStreamWriter(outputStream, charset))
                .withSeparator(delimiter)
                .build();
    }

    public void writeHeader(String[] header) {
        if (!empty) {
            throw new IllegalStateException("Cannot write header. CSV file has already been written to.");
        }
        writer.writeNext(header);
    }

    public void write(String str) {
        empty = false;
        currentRecord.add(str);
    }

    public void endRecord() {
        writer.writeNext(currentRecord.toArray(new String[0]));
        currentRecord.clear();
    }

    public void flush() {
        writer.flushQuietly();
    }
}
